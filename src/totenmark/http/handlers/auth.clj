(ns totenmark.http.handlers.auth
  (:require [clojure.string :as str]
            [totenmark.db.tokens :as db.tokens]
            [totenmark.db.users :as db.users]
            [totenmark.http.response :as response]
            [totenmark.logic.password :as password]
            [totenmark.utils.jwt :as jwt]))

(defn login!
  [request]
  (let [{:keys [email password]} (:json-params request)]
    (if-not (and (string? email) (string? password))
      (response/json-response 422 {:msg "Informe e-mail e senha."})
      (try
        (let [email (-> email str/trim str/lower-case)
              user (db.users/find-by-email email)]
          (if (and user (password/valid-password? password (:password user)))
            (response/json-response 200 {:msg "Login realizado."
                                         :token (jwt/generate user)})
            (response/json-response 401 {:msg "E-mail ou senha inválidos."})))
        (catch Exception exception
          (response/failure "login" exception 500 "Não foi possível fazer login."))))))

(defn logout!
  [request]
  (try
    (db.tokens/revoke! (:identity request))
    (response/json-response 200 {:msg "Sessão encerrada."})
    (catch Exception exception
      (response/failure "logout" exception 500 "Não foi possível encerrar a sessão."))))
