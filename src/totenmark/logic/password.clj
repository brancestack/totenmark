(ns totenmark.logic.password
  (:import [org.mindrot.jbcrypt BCrypt]))

(def ^:private work-factor 12)

(def ^:private bcrypt-max-bytes 72)

(defn supported-length?
  [password]
  (and (string? password)
       (<= (alength (.getBytes ^String password
                              java.nio.charset.StandardCharsets/UTF_8))
           bcrypt-max-bytes)))

(defn hash-password
  [password]
  (when-not (supported-length? password)
    (throw (ex-info "A senha ultrapassa o limite seguro do BCrypt."
                    {:max-bytes bcrypt-max-bytes})))
  (BCrypt/hashpw password (BCrypt/gensalt work-factor)))

(defn valid-password?
  [password hash]
  (and (string? password)
       (string? hash)
       (supported-length? password)
       (try
         (BCrypt/checkpw password hash)
         (catch IllegalArgumentException _
           false))))
