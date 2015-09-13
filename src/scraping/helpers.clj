(ns scraping.helpers
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [net.cgrand.enlive-html :as html]
            ))



(defn make-html-resource [string-content]
  (-> string-content java.io.StringReader. html/html-resource))

(defn filter-digits [s]
  (str/replace s #"\D" ""))

(defn delete-space-comma [s]
  (str/replace s #",|\s" ""))

(defn lazy-flatten1 [s]
  (for [ss s, sss ss] sss))

(defn replace-swp [match repl s]
  (str/replace s match repl))

(defn cut-columns [xs cols]
  (map #(get xs %) cols))

(defn space-empty-comma [line]
   (->> line (replace-swp #",(?=,)|,$" ", ") (replace-swp #"^," " ,")))

(defn scrap-first-match [content css-sel & fs]
  (reduce #(%2 %1)
          (-> content
              (html/select css-sel) first
              html/text delete-space-comma)
          fs))

(defn pipe [in-file get-out-file process-lines]
  (do (println in-file)
      (with-open [rdr (io/reader in-file)]
        (with-open [wrt (io/writer (get-out-file in-file))]
          (doseq [line (process-lines (line-seq rdr))]
            (.write wrt (str line "\n")))))))

(defn write-seq [out-file s]
  (with-open [wrt (io/writer out-file)]
    (doseq [line s]
      (.write wrt (str line "\n")))))
