(ns c6h6.web
  (:require [compojure.core :refer [defroutes GET PUT POST DELETE ANY]]
            [compojure.handler :refer [site]]
            [compojure.route :as route]
            [clojure.java.io :as io]
            [ring.adapter.jetty :as jetty]
            [environ.core :refer [env]])
  (:require [c6h6.utils :as utils :refer [defhandler response success fail]]
            [c6h6.oauth :as oauth]
            [c6h6.models :as models]))

(defhandler foo [a b]
  (response {:a a :b b} 200))

(defhandler insert-saying [content]
  (success (models/create-saying content)))

(defhandler gets-saying [content]
  (success (models/gets-saying)))

(defroutes app
  (GET "/" [a b]
       foo)

  (POST "/saying" [content]
        insert-saying)

  (GET "/saying" []
       gets-saying)

  (GET "/oauth_url" [return_url]
       oauth/get-oauth-url)

  (GET "/github_oauth/callback" [error code state]
       oauth/oauth-callback)

  (ANY "*" []
       (route/not-found (slurp (io/resource "404.html")))))

(defn -main [& [port]]
  (let [port (Integer. (or port (env :port) 5000))]
    (jetty/run-jetty (site #'app) {:port port :join? false})))
