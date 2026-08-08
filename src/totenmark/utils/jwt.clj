(ns totenmark.utils.jwt
  (:require [buddy.sign.jwt :as jwt]
            [totenmark.config :as config]))

(defn- epoch-seconds
  []
  (.getEpochSecond (java.time.Instant/now)))

(defn generate
  [user]
  (let [issued-at (epoch-seconds)]
    (jwt/sign
     {:user-id (:id user)
      :email   (:email user)
      :iat     issued-at
      :exp     (+ issued-at (config/jwt-ttl-seconds))}
     (config/jwt-secret))))

(defn verify
  [token]
  (let [claims (jwt/unsign token (config/jwt-secret))]
    (when (<= (:exp claims 0) (epoch-seconds))
      (throw (ex-info "Token has expired." {:type ::expired-token})))
    claims))
