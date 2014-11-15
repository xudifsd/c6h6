(defproject c6h6 "1.0.0-SNAPSHOT"
  :description "resource app for hook2do"
  :url "https://github.com/xudifsd/c6h6"
  :license {:name "Eclipse Public License v1.0"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [compojure "1.1.8"]
                 [ring/ring-jetty-adapter "1.2.2"]
                 [environ "0.5.0"]
                 [log4j "1.2.17"]
                 [org.clojure/tools.logging "0.3.0"]
                 [clj-http "0.9.2"]
                 [org.clojure/data.json "0.2.5"]
                 [org.clojure/java.jdbc "0.3.5"]
                 [postgresql "9.1-901-1.jdbc4"]]
  :min-lein-version "2.0.0"
  :plugins [[environ/environ.lein "0.2.1"]]
  :hooks [environ.leiningen.hooks]
  :uberjar-name "c6h6-standalone.jar"
  :profiles {:production {:env {:production true}}}
  :main c6h6.web)
