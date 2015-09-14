(ns clj.twitter-test
  (:require [midje.sweet :refer :all]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [clj.core :as core]
            [clj.twitter :refer :all]))


(facts "about `get-next-batch`"
       (get-next-batch "me" [{:id 1}]) => [{:id 2}]
       
       (seq (get-next-batch "me" nil)) => nil

       (against-background
         (get-user-timeline "me" 1) => [{:id 2}]))

(facts "about `get-tweets`"
       (against-background
         [(get-user-timeline anything) => [{:id 0}]
          (go2nxt16-batches "nil" anything) => nil ]

         (fact "get-tweets should stop iterating when first nil encoutered"
               (with-redefs [;get-user-timeline (fn [& args][{:id 0}])
                             ;go2nxt16-batches (fn [sc prev-state] nil)
                             ]
                 (doall (get-tweets "nil")) => [{:id 0}]))

         (fact "get-tweets should run 16 iterations at most"
               (with-redefs [go2nxt16-batches (fn [sc [i p-b]]
                                                (if (< i 15) [(inc i) ["iterate -> 16"]]))]
                 (count (get-tweets "regular"))) => 16)

         ))

(facts "about `get-path-out`" 
      (get-path-out "/raw/" "troll") => "/raw/troll_twitter.json")
