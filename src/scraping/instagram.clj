(ns clj.instagram
  (:require 
    [cheshire.core :refer :all]
    [cheshire.generate :refer [add-encoder encode-seq remove-encoder]]
    [clj-http.client :as client]))

(def header {"User-Agent" "Mozilla/5.0 (X11; Linux i586; rv:31.0) Gecko/20100101 Firefox/31.0"})

(defn make-url [user]
  (str "https://instagram.com/" user "/media/"))

(defn get-items [response]
  (let [body (parse-string (get response :body) true)]
    (get body :items)))

(defn get-next-batch
  ([user prev-batch] (if-let [max-id (re-find #"^\d+"
                                              (get (last prev-batch) :id ""))
                              max_timestamp "1356908400" ;;31/12/12
                              ]
                       (do (Thread/sleep 2000)
                           (client/get (make-url user) 
                                       {:header header
                                        :query-params {"max_id" max-id
                                                       "max_timestamp" max_timestamp
                                                       }
                                        :accept :json}))))
  ([user] (client/get (make-url user)
                      {:header header
                       :accept :json})))

(defn instagram-responses [user]
  (let [first-items (-> user get-next-batch get-items)]
    (take-while identity
                (iterate #(->> % (get-next-batch user) get-items)
                         first-items))))

(defn instagram-posts [responses]
  (for [response responses, post response]
    post))

