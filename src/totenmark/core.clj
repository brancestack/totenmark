(ns totenmark.core
  (:require [totenmark.http.server :as server]))

(defn -main
  []
  (server/start!))
