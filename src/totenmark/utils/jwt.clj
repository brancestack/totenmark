(ns totenmark.utils.jwt
  (:require [buddy.sign.jwt :as jwt]
            [clojure.string :as str]
            [totenmark.config :as config]))

(defn- epoch-seconds
  []
  (.getEpochSecond (java.time.Instant/now)))

(defn generate
  [user]
  (let [issued-at (epoch-seconds)]
    (jwt/sign
     {:user-id (:id user)
      :session-version (:session-version user 0)
      :jti     (str (random-uuid))
      :iat     issued-at
      :exp     (+ issued-at (config/jwt-ttl-seconds))}
     (config/jwt-secret))))

(defn- valid-claims?
  [{:keys [user-id session-version jti iat exp]}]
  (and (integer? user-id)
       (pos? user-id)
       (integer? session-version)
       (not (neg? session-version))
       (string? jti)
       (not (str/blank? jti))
       (integer? iat)
       (integer? exp)
       (< iat exp)))

(defn verify
  [token]
  (let [claims (-> (jwt/unsign token (config/jwt-secret))
                   (update :session-version #(or % 0)))]
    (when-not (valid-claims? claims)
      (throw (ex-info "O token não contém as claims esperadas."
                      {:type ::invalid-claims})))
    (when (<= (:exp claims 0) (epoch-seconds))
      (throw (ex-info "O token expirou." {:type ::expired-token})))
    claims))
