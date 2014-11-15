(ns c6h6.web
  (:require [compojure.core :refer [defroutes GET PUT POST DELETE ANY]]
            [compojure.handler :refer [site]]
            [compojure.route :as route]
            [clojure.java.io :as io]
            [ring.adapter.jetty :as jetty]
            [environ.core :refer [env]])
  (:require [c6h6.utils :as u :refer [defhandler response success fail]]
            [c6h6.oauth :as oauth]
            [c6h6.models :as models]
            [c6h6.hook :as hook]))

(defhandler not-implemented []
  {:status 501
   :headers {"Content-Type" "text/plain"}
   :body "not implemented"})

(defhandler foo [a b]
  (response {:a a :b b} 200))

(defroutes route
  (GET "/" [a b]
       foo)

  (GET "/oauth_url" [return_url uid]
       oauth/get-oauth-url)

  (GET "/github_oauth/callback" [error code state]
       oauth/oauth-callback)

  (POST "/github/hook/:uid" []
        hook/hook-for-github)

  (GET "/github_oauth/list" []
       oauth/gets-oauth)

  (ANY "*" []
       (route/not-found (slurp (io/resource "404.html")))))

(def app (-> app
           u/wrap-json-params))

(defn -main [& [port]]
  (let [port (Integer. (or port (env :port) 5000))]
    (jetty/run-jetty (site #'app) {:port port :join? false})))
