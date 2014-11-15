(ns c6h6.oauth
  (:require [clojure.tools.logging :as log]
            [clojure.tools.macro :refer [name-with-attributes]]
            [clj-http.client :as http]
            [clojure.data.json :as json])
  (:require [c6h6.utils :as u :refer [success fail defhandler]]
            [c6h6.models :as models]))

(defonce github-oauth-params
  (let [client-id "fc1bb1728f0feb81a7be"]
    {:client-id client-id
     :client-sec "61ae642122b4438757f53178dca1ffbf0dde71fa"
     :callback (str "https://c6h6.herokuapp.com/github_oauth/callback")
     :oauth-url (str "https://github.com/login/oauth/authorize?client_id="
                     client-id
                     "&redirect_uri=https://c6h6.herokuapp.com/github_oauth/callback&scope=repo")}))

(defn gen-oauth-url [state]
  (str (:oauth-url github-oauth-params) "&state=" state))

(defn get-auth [code]
  (let [{:keys [client-id client-sec callback]} github-oauth-params
        url "https://github.com/login/oauth/access_token"
        resp (http/post url {:accept :json
                             :form-params {:client_id client-id
                                           :client_secret client-sec
                                           :code code
                                           :redirect_uri callback}})]
    (log/debug "in get-auth github\n"
             "resp" resp)
    (when (= 200 (-> resp :status))
      (let [auth-result (-> resp :body (json/read-str :key-fn keyword))]
        (log/debug "auth-result" auth-result)
        auth-result))))

(defn parse-state
  "state is like return_url|xxx|uid|xxx"
  [state]
  (log/debug "state is " state)
  (let [[_ return_url _ uid] (clojure.string/split state #"\|")]
    (u/create-kw-map return_url uid)))

(defn redirect-to-return_url
  [return_url]
  {:status 302
   :headers {"Location" return_url}
   :body ""})

(defhandler get-oauth-url
  [return_url uid]
  (let [state (ring.util.codec/url-encode
                (format "return_url|%s|uid|%s"
                        return_url
                        uid))]
    (gen-oauth-url state)))

(defhandler oauth-callback
  [error code state]
  {:pre-check [(every? u/nil-or-empty? [error code]) "missing paramas"]}
  (if (u/nil-or-empty? error)
    (if-let [auth (get-auth code)]
      (if-not (:error auth)
        (let [access_token (get auth "access_token")
              expires_in (get auth "expires_in")
              refresh_token (get auth "refresh_token")
              {:keys [return_url uid]} (parse-state state)
              _ (log/debug "result of parse-state\n"
                           "return_url" return_url "\n"
                           "uid" uid "\n"
                           )
              thirdparty (-> (u/create-kw-map uid
                                              access_token
                                              refresh_token
                                              expires_in)
                           (u/dissoc-if-nil-empty [:refresh_token :expires_in]))
              _ (log/info "thirdparty " thirdparty)]
          (models/create-thirdparties thirdparty)
          (redirect-to-return_url return_url))
        (fail 401 (:error_description auth)))
      (fail 401 (str "using '"
                     code
                     "' to get access_code failed")))
    (fail 401 (str "auth failed: " error))))

(defhandler gets-oauth []
  (success (models/gets-thirdparties)))
