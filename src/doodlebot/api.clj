(ns doodlebot.api
  (:require [org.httpkit.client :as http]
            [cheshire.core :refer [generate-string parse-string]]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [clj-time.periodic :as p]
            [doodlebot.utils :refer :all])
  (:import [javax.net.ssl
            SSLEngine
            SSLParameters
            SNIHostName]
           [java.net URI]
           [java.util Date]))


;;;; --- Private API --- ;;;;

(def ^:dynamic doodle-base-url "https://doodle.com/api/v2.0")

(defn- sni-configure
  [^SSLEngine ssl-engine ^URI uri]
  (let [^SSLParameters ssl-params (.getSSLParameters ssl-engine)]
    (.setServerNames ssl-params [(SNIHostName. (.getHost uri))])
    (.setSSLParameters ssl-engine ssl-params)))

(def ^:dynamic client (http/make-client {:ssl-configurer sni-configure}))

(defn- make-address
  [endpoint]
  (str doodle-base-url endpoint))

(defn- post
  [endpoint payload]
  (let [response @(http/post (make-address endpoint)
                             {:client client
                              :keep-alive 3000
                              :headers {"content-type" "application/json"}
                              :body payload})]
    (when (= (:status response) 200)
      response)))

(defn- post-json
  [endpoint payload-str]
  (-> (post endpoint (generate-string payload-str))
      :body
      (parse-string true)))


;;;; --- Public API --- ;;;;

(defn date->opt
  "Given a joda time returns a correctly formatted option for doodle."
  [date]
  {:allday true
   :start (c/to-long date)
   :end nil})

(def default-opts
  {:initiator {:notify true
               :timeZone "Etc/GMT+12"}
   :participants []
   :comments []
   :type "DATE"
   :description ""
   :preferencesType "YESNOIFNEEDBE"
   :hidden false
   :askAddress false
   :askEmail false
   :askPhone false
   :locale "en"})

(defn make-poll
  "Makes a new doodle poll."
  [opts]
  (let [opts (deep-merge default-opts opts)]
    (post-json "/polls" (deep-merge default-opts opts))))

(defn poll-url
  [id]
  (str "https://doodle.com/poll/" id))

(comment

  (def start-date (t/local-date 2019 4 22))

  (def date-opts
    (->> (p/periodic-seq start-date (t/days 1))
         (map date->opt)
         (take 7)))

  (def d
    (make-poll {:title "Dnd wen"
                :options date-opts
                :initiator {:name "My epic name"
                            :email "me@example.com"}}))

  [])
