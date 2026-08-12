(ns totenmark.http.auth
  (:require [clojure.string :as str]
            [totenmark.db.tokens :as db.tokens]
            [totenmark.db.users :as db.users]
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
         (let [claims (jwt/verify token)
               session-check
               (try
                 (let [user (db.users/auth-state (:user-id claims))]
                   {:valid? (and user
                                 (not (db.tokens/revoked? (:jti claims)))
                                 (= (:session-version claims)
                                    (:session-version user)))})
                 (catch Exception exception
                   {:failure (response/failure
                              "authenticate-session"
                              exception
                              500
                              "Não foi possível validar a sessão.")}))]
           (cond
             (:failure session-check)
             (assoc context :response (:failure session-check))

             (:valid? session-check)
             (assoc-in context [:request :identity] claims)

             :else
             (assoc context :response
                    (response/json-response 401 {:msg "Esta sessão não é mais válida."}))))
         (catch Exception _
           (assoc context :response
                  (response/json-response 401 {:msg "Token inválido ou expirado."}))))
       (assoc context :response
              (response/json-response 401 {:msg "Envie o token no cabeçalho Authorization."}))))})

(defn owns-user?
  [request id]
  (= (get-in request [:identity :user-id]) id))
