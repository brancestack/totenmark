(ns totenmark.logic.password
  (:import [org.mindrot.jbcrypt BCrypt]))

(def ^:private work-factor 12)

(defn hash-password
  [password]
  (BCrypt/hashpw password (BCrypt/gensalt work-factor)))

(defn valid-password?
  [password hash]
  (BCrypt/checkpw password hash))
