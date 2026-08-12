(ns totenmark.logic.password-test
  (:require [clojure.test :refer [deftest is]]
            [totenmark.logic.password :as password]))

(deftest invalid-stored-passwords-do-not-break-login
  (is (false? (password/valid-password? "password123" nil)))
  (is (false? (password/valid-password? "password123" "not-a-bcrypt-hash")))
  (let [hash (password/hash-password "password123")]
    (is (password/valid-password? "password123" hash))
    (is (false? (password/valid-password? "wrong-password" hash)))))

(deftest rejects-passwords-beyond-bcrypts-byte-limit
  (let [prefix (apply str (repeat 72 "a"))
        hash (password/hash-password prefix)]
    (is (password/supported-length? prefix))
    (is (password/valid-password? prefix hash))
    (is (false? (password/supported-length? (str prefix "b"))))
    (is (false? (password/valid-password? (str prefix "b") hash)))
    (is (thrown? clojure.lang.ExceptionInfo
                 (password/hash-password (str prefix "b"))))
    (is (false? (password/supported-length? (apply str (repeat 19 "🔐")))))))
