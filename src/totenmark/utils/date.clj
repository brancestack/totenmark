(ns totenmark.utils.date)

(defn now
  []
  (str (java.time.Instant/now)))

(defn after-seconds
  [seconds]
  (str (.plusSeconds (java.time.Instant/now) seconds)))
