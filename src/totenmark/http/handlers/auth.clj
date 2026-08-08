(ns totenmark.http.handlers.auth
  (:require [totenmark.db.users :as db.users]
            [totenmark.http.response :as response]
            [totenmark.logic.password :as password]
            [totenmark.utils.jwt :as jwt]))

(defn login!
  [request]
  (let [{:keys [email password]} (:json-params request)]
    (if-not (and (string? email) (string? password))
      (response/json-response 422 {:msg "email and password are required."})
      (try
        (let [user (db.users/find-by-email email)]
          (if (and user (password/valid-password? password (:password user)))
            (response/json-response 200 {:msg "Login successful."
                                         :token (jwt/generate user)})
            (response/json-response 401 {:msg "Invalid email or password."})))
        (catch Exception exception
          (response/failure "login" exception 500 "Failed to authenticate."))))))
