(ns totenmark.http.auth
  (:require [clojure.string :as str]
            [totenmark.http.response :as response]
            [totenmark.utils.jwt :as jwt]))

(defn- bearer-token
  [request]
  (let [header (get-in request [:headers "authorization"])]
    (when (string? header)
      (second (re-matches #"(?i)^Bearer\s+(.+)$" (str/trim header))))))

(def authenticate
  {:name ::authenticate
   :enter
   (fn [context]
     (if-let [token (bearer-token (:request context))]
       (try
         (assoc-in context [:request :identity] (jwt/verify token))
         (catch Exception _
           (assoc context :response
                  (response/json-response 401 {:msg "Invalid or expired token."}))))
       (assoc context :response
              (response/json-response 401 {:msg "Bearer token is required."}))))})

(defn owns-user?
  [request id]
  (= (get-in request [:identity :user-id]) id))
