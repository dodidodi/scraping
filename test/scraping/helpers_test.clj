(ns scraping.helpers-test
  (:require [midje.sweet :refer :all]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [net.cgrand.enlive-html :as html]
            [scraping.helpers :refer :all]))


(facts "About `make-html-resource`"
       (-> html-snippet make-html-resource (html/select [:div.c]) first html/text) => "hello" 
       (background
         (before :contents
                 (def html-snippet "<div class=\"c\">hello</div>"))))


(facts "About `scrap-first-match`"
       (scrap-first-match html-snippet [:title] #(str/replace % "Audi" "") #(str/replace % "New" "")) =not=> 
       (contains #"NewAudi")
       (scrap-first-match html-snippet [:title]) => "NewAudi"
       (background
         (before :contents
                 (def html-snippet (make-html-resource "<title>New Audi</title>")))))

(fact "lazy-flatten1 should flatten 1 level of depth"
      (lazy-flatten1 [[1] [2]]) => [1 2])

(fact "cut-columns should filter for given cols"
    (cut-columns (vec (range 10 20)) [3 5]) => [13 15])

(facts "About `filter-digits`"
       (filter-digits "detr123zu") => "123")

(fact "space-empty-comma should 1-space consecutives commas"
      (space-empty-comma "1,,,3") => "1, , ,3")

(fact "space-empty-comma should space last comma"
             (space-empty-comma "1,,") => "1, , ")

(fact "space-empty-comma should space first comma"
             (space-empty-comma ",,1") => " , ,1")
