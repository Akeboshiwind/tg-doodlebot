(ns doodlebot.core
  (:require [clojure.string :as str]
            [clj-time.core :as time]
            [clj-time.periodic :as pe]
            [clj-time.format :as fo]
            [morse.handlers :as h]
            [morse.api :as t]
            [morse.polling :as p]
            [doodlebot.utils :as u]
            [doodlebot.api :as api])
  (:gen-class))

(def token "158254207:AAFCJdxomPmcXvu0wI7QCCnncHtlhKi8pLY")

;; Shape:
(comment

  (s/def ::title string?)
  (s/def ::name string?)
  (s/def ::email string?)
  (s/def ::start-date string?)
  (s/def ::duration string?)
  (s/def ::settings (s/keys ::req-un [::title
                                      ::name
                                      ::email
                                      ::start-date
                                      ::duration]))

  (s/def ::id string?)
  (s/def ::poll (s/keys ::req-un [::id
                                  ::title]))
  (s/def ::previous-polls (s/coll-of ::poll))

  (s/def ::option #{:title :name :email :start-date :duration})
  (s/def ::current-selection (s/or :option ::option
                                   :nil nil?))

  (s/def ::settings-message-id int?)

  (s/def ::prompt-message-id int?)

  (s/def ::user-opts (s/keys ::req-un [::settings
                                       ::settings-message-id
                                       ::prompt-message-id
                                       ::previous-polls
                                       ::current-selection]))

  (s/def ::db (s/map-of ::from-id ::user-opts))

  [])

(def db (atom {}))

(defn inline-keyboard-button
  [text callback-data]
  {:text text
   :callback_data callback-data})

(defn settings-text
  [{:keys [title name email start-date duration]}]
  (str "Current settings:"
       "\nTitle: " title
       "\nName: " name
       "\nEmail: " email
       "\nStart Date (YYYY-MM-DD): " start-date
       "\nDuration (days): " duration))

(defn settings-keyboard
  [show-create?]
  (let [k {:inline_keyboard
           [[{:text "Title" :callback_data "title"}
             {:text "Your Name" :callback_data "name"}
             {:text "Your Email" :callback_data "email"}]
            [{:text "Start Date" :callback_data "start-date"}
             {:text "Duration" :callback_data "duration"}]]}]
    (if show-create?
      (assoc-in k [:inline_keyboard 1 2]
                {:text "Create Poll" :callback_data "create-poll"})
      k)))

(defn full-settings?
  [settings]
  (every? (complement nil?)
          (map #(get settings %)
               [:title :name :email :start-date :duration])))

(defn settings-menu
  [chat-id settings]
  (t/send-text token chat-id
               {:reply_markup
                (settings-keyboard (full-settings? settings))}
               (settings-text settings)))

(defn update-settings-menu
  [chat-id message-id settings]
  (t/edit-text token chat-id message-id
               {:reply_markup
                (settings-keyboard (full-settings? settings))}
               (settings-text settings)))

(def ymd-fmt (fo/formatters :year-month-day))

(defn start
  [{{id :id} :chat
    {from :id
     fname :first_name
     lname :last_name} :from}]
  (swap! db
         (fn [db]
           (let [settings (get-in db [from :settings])]
             (assoc-in db [from :settings]
                       (u/deep-merge (select-keys settings [:email :duration])
                                     {:name (str fname " " lname)
                                      :start-date (fo/unparse ymd-fmt (time/now))})))))
  (let [{:keys [settings]} (get @db from)
        {{message-id :message_id} :result} (settings-menu id settings)]
    (swap! db
           (fn [db]
             (assoc-in db [from :settings-message-id]
                       message-id)))))

(defn help
  [{{id :id :as chat} :chat}]
  (println "Help was requested in " chat)
  (t/send-text token id "Help is on the way"))

(defn make-poll
  [{{id :id :as chat} :chat
    text :text}]
  (let [text (rest (str/split text #" "))]
    (when-not (empty? text)
      (println "Given text is:" text)
      (t/send-text token id "Make one here: https://doodle.cam/"))))

(defn make-poll-inline
  [{{id :id :as chat} :chat
    {from :id} :from
    query-id :id
    query :query
    :as inline}]
  (when inline
    (println "Inline:" inline)
    (let [polls (get-in @db [from :previous-polls])]
      (println polls)
      (t/answer-inline token query-id
                       {:switch_pm_text "Create New poll"
                        :switch_pm_parameter "test"
                        :cache_time 0}
                       (map (fn [{:keys [id title]}]
                              {:type "article"
                               :id (u/uuid)
                               :title title
                               :input_message_content {:message_text (str "https://doodle.com/poll/" id)}})
                            polls)))))

(defn make-poll-callback
  [{{{id :id :as chat} :chat} :message
    {from :id} :from
    callback-id :id
    data :data
    :as callback}]
  (when callback
    (println "Callback:" callback)
    (let [{{message-id :message_id} :result}
          (case data
            "title" (t/send-text token id "Set the title for your poll:")
            "name" (t/send-text token id "Set your name for your poll:")
            "email" (t/send-text token id "Set your email for your poll:")
            "start-date" (t/send-text token id "Set the start date for your poll:")
            "duration" (t/send-text token id "Set the duration for your poll:")
            "create-poll" (let [{:keys [title name email start-date duration]}
                                (get-in @db [from :settings])
                                start-date
                                (fo/parse ymd-fmt start-date)
                                {poll-id :id}
                                (api/make-poll {:title title
                                                :initiator {:name name
                                                            :email email}
                                                :options (->> (pe/periodic-seq start-date (time/days 1))
                                                              (map api/date->opt)
                                                              (take (Integer/parseInt duration)))})]
                            (swap! db (fn [db]
                                        (let [polls (get-in db [from :previous-polls])]
                                          (assoc-in db [from :previous-polls]
                                                    (conj polls {:id poll-id
                                                                 :title title})))))
                            (t/send-text token id
                                         {:reply_markup
                                          {:inline_keyboard
                                           [[{:text "Back to chat" :switch_inline_query ""}]]}}
                                         "Poll created:"))
            nil)]
      (when-not (= "create-poll" data)
        (swap! db (fn [db]
                    (-> db
                        (assoc-in [from :current-selection] data)
                        (assoc-in [from :prompt-message-id] message-id)))))
      (t/answer-callback token callback-id ""))))

(defn handle-message
  [{{id :id} :chat
    {from :id} :from
    text :text
    :as msg}]
  (when msg
    (println "Message:" msg)
    (let [{:keys [current-selection]} (get @db from)
          {message-id :message_id} msg]
      (when current-selection
        (case current-selection
          "title" (swap! db assoc-in [from :settings :title] text)
          "name" (swap! db assoc-in [from :settings :name] text)
          "email" (swap! db assoc-in [from :settings :email] text)
          "start-date" (swap! db assoc-in [from :settings :start-date] text)
          "duration" (swap! db assoc-in [from :settings :duration] text)
          nil)
        (swap! db assoc-in [from :current-selection] nil)
        (let [{:keys [settings settings-message-id prompt-message-id]} (get @db from)]
          (t/delete-text token id prompt-message-id)
          (t/delete-text token id message-id)
          (update-settings-menu id settings-message-id settings))))))

(h/defhandler doodle-bot
  ;; Standard commands
  (h/command-fn "start" (fn [& args] (apply (var-get #'start) args)))
  (h/command-fn "help" help)

  ;; Bot functionality
  (h/command-fn "poll" (fn [& args] (apply (var-get #'make-poll) args)))
  (h/inline-fn (fn [& args] (apply (var-get #'make-poll-inline) args)))
  (h/callback-fn (fn [& args] (apply (var-get #'make-poll-callback) args)))

  ;; Spying on everyone
  (h/message-fn (fn [& args] (apply (var-get #'handle-message) args))))

(def channel (p/start token doodle-bot))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!"))

(comment

  [])
