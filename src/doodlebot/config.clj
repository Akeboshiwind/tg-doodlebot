(ns doodlebot.config
  (:require [environ.core :as e]
            [yaml.core :as yaml]))

(def ^{:private true} cfg
  (atom {}))

(defn config
  ([& ks]
   (get-in @cfg ks)))

(defn env->config
  [env]
  {:token (env :doodlebot-token)})

(defn remove-nils
  "remove pairs of key-value that has nil value from a (possibly nested) map. also transform map to nil if all of its value are nil"
  [nm]
  (clojure.walk/postwalk
   (fn [el]
     (if (map? el)
       (let [m (into {} (remove (comp nil? second) el))]
         (when (seq m)
           m))
       el))
   nm))

(defn load!
  ([]
   (swap! cfg
          merge
          (remove-nils (env->config e/env))))
  ([config-file]
   (swap! cfg
          merge
          (remove-nils (yaml/from-file config-file))
          (remove-nils (env->config e/env)))))

(comment

  (load! "doodlebot.yml")

  (config :token)

  [])
