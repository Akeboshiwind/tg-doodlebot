(defproject doodlebot "0.1.0-SNAPSHOT"

  :description "FIXME: write description"

  :license {:name "MIT"}

  :dependencies [[org.clojure/clojure "1.10.0"]
                 [morse "0.4.2"]
                 [http-kit "2.3.0"]
                 [cheshire "5.8.1"]
                 [clj-time "0.15.0"]]

  :main ^:skip-aot doodlebot.core

  :target-path "target/%s"

  :profiles {:uberjar {:aot :all}})
