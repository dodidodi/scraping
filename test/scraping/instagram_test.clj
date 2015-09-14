(ns clj.instagram-test
  (:require [midje.sweet :refer :all]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [clj-http.client :as client]
            [clj.instagram :refer :all]
            ))



(fact "`make-url` should return an instagram user url"
      (make-url "ok") => "https://instagram.com/ok/media/")

(fact "`get-items` should return a jsonised posts array"
    (let [response {:body "{\"items\":[{\"id\":\"ok\"},{\"id\":\"ok2\"}]}"}]
        (get-items response) => [{:id "ok"} {:id "ok2"}]))

(facts "about `get-next-batch`"
       (get-next-batch "user") => :first-response
       (get-next-batch "user" []) => nil?
       (get-next-batch "user" [{:id "12_3"}]) => :next-response
       (against-background
        (client/get anything (checker [params] (= 2 (count params)))) => :first-response
        (client/get anything (checker [params] (= 3 (count params)))) => :next-response))

(facts "about `instagram-responses`"
       (against-background 
         (before :contents (def a (atom [1 2 3])))
         (get-next-batch & anything) => nil)
       (with-redefs [get-items (fn [& args] (let [res (first @a)]
                                              (do (swap! a next)
                                                  res)))]
         (instagram-responses "user") => [1 2 3]))

(facts "about `instagram-posts`"
       (instagram-posts [[1] [2]]) => seq?
       (instagram-posts [[1] [2]]) => [1 2]
       (instagram-posts nil) => empty?

       )
