(ns totenmark.utils.date)

(defn now
  []
  (.format (java.text.SimpleDateFormat. "yyyy/MM/dd hh:mm:ss")
           (new java.util.Date)))
