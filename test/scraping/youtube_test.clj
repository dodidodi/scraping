(ns scraping.youtube-test
  (:require [midje.sweet :refer :all]
            [cheshire.core :refer :all]
            [net.cgrand.enlive-html :as html]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [clj-http.client :as client]
            [clj-http.fake :refer [with-fake-routes]]
            [scraping.helpers :as helpers]
            [scraping.youtube :refer :all]
            ))


(defn get-snippet-get-response-text [response k]
  (->  response k (html/select [:div]) first html/text))



(against-background
  [(before :contents (def video-json {:body (-> "youtube_video.html" io/resource slurp (parse-string true) :body
                                                java.io.StringReader. html/html-resource)}))
   (before :contents (def sentiments {:likes "3268"
                                      :dislikes "119"
                                      :views "1170768"
                                      :followers "77426"
                                      :date "May132013"}))
   (before :contents
           (def frontpage-json {:body (-> "youtube_frontpage.json" io/resource slurp (parse-string true)
                                          :body java.io.StringReader. html/html-resource)}))
   (before :contents
           (def ajax-json (let  [resp (-> "youtube_ajax.json" io/resource slurp (parse-string true))]
                            {:content_html
                             (-> resp :content_html java.io.StringReader. html/html-resource)
                             :load_more_widget_html
                             (-> resp :load_more_widget_html java.io.StringReader. html/html-resource)})))
   (before :contents
           (def html-snippet "<div class=\"c\">hello</div>"))]


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;http

  (with-fake-routes 
    {#"https://www.youtube.com/user/(\w+)/videos"
     (fn [request] {:status 200 :headers {} :body "<div>frontpage-ok</div>"})
     #"https://www.youtube.com//browse_ajax\?action_(.*)$"
     (fn [request] {:status 200 :headers {} :body (generate-string
                                                    {:content_html "<div>ajax-content</div>"
                                                     :load_more_widget_html "<div>ajax-loadmore</div>"})})
     #"https://www.youtube.com//watch(.*)$"
     (fn [request] {:status 200 :headers {} :body "<html>video-ok</html>"})}

    (facts "About `fetch-frontpage`"
           (-> "ArenaPeople" fetch-frontpage (get-snippet-get-response-text :body)) => "frontpage-ok")

    (facts "About `fetch-next-frame`"
           (let [url "/browse_ajax?action_continuation=1&continuation=4qmFsgJAEhhVQ0xoSzRfSEYtTEV0QkM3MUxZTDJaNGcaJEVnWjJhV1JsYjNNZ0FEZ0JZQUZxQUhvQk03Z0JBQSUzRCUzRA%253D%253D"]
             (-> url fetch-next-frame (get-snippet-get-response-text :content_html)) => "ajax-content"
             (-> url fetch-next-frame (get-snippet-get-response-text :load_more_widget_html)) => "ajax-loadmore"
             ))

    (facts "About `fetch-video`"
           (-> (fetch-video "/watch?v=xZjykPITuq0") :body
               (html/select [:html]) first html/text)  => "video-ok")

    (facts "About `fetch`"
           (fetch {:request "user"}) => "front"
           (provided
             (fetch-frontpage "user") => "front")

           (fetch {:request "/browse"}) => "frame"
           (provided
             (fetch-next-frame "/browse") => "frame")

           (fetch {:request "/watch"}) => "video"
           (provided
             (fetch-video "/watch") => "video")))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;frame

  (facts "About `get-followers`"
         (-> "" helpers/make-html-resource get-followers) => ""
         (-> frontpage-json :body get-followers) => "3527")

  (facts "About `get-body`"
         (get-body {:response {:body "yo" :headers "h"}}) => "yo"
         (get-body {:response {:content_html "yo" :headers "h"}}) => "yo")

  (facts "About `get-thumbs-duration`"
         (count (get-thumbs-duration (:body frontpage-json))) => 30
         (count (get-thumbs-duration (:content_html ajax-json))) => 30
         (get-thumbs-duration (:body frontpage-json)) => (has every? (contains #"[0-9:]+"))
         (get-thumbs-duration (:content_html ajax-json)) => (has every? (contains #"[0-9:]+"))
         )

  (facts "About `get-thumbs-url`"
         (get-thumbs-url (:body frontpage-json)) => (has every? (contains #"^/watch"))
         (count (get-thumbs-url (:body frontpage-json))) => 30
         (count (get-thumbs-url (:content_html ajax-json))) => 30)

  (facts "About `scrap-frame`"
         (scrap-frame {:error anything}) => (just {:error anything})
         (scrap-frame {:response ajax-json}) => (just {:response ajax-json
                                                       :thumbs-url (n-of #"^/watch" 30)
                                                       :durations (n-of #"[0-9:]+" 30)})
         )

  (facts "About `get-ajax-url`"
         (-> "<div>stopped</div>" helpers/make-html-resource get-ajax-url) => nil?
         (get-ajax-url (:load_more_widget_html ajax-json)) =not=> nil?
         (get-ajax-url (:body frontpage-json)) =not=> nil?
         (get-ajax-url (:body frontpage-json)) => (has-prefix "/browse_ajax")
         (get-ajax-url (:load_more_widget_html ajax-json)) => (has-prefix "/browse_ajax"))

  (facts "About `get-next-frame`"
         (get-next-frame anything) => (just {:response ajax-json
                                             :request #"^/browse"})
         (provided
           (fetch anything) => ajax-json)
         )

  (facts "About `get-frames`"
         (-> {:request "one"} get-frames first :response) => frontpage-json
         (-> {:request "one"} get-frames first :followers) => "3527"
         (-> {:request "one"} get-frames first :request) => (has-prefix "/browse_ajax") 
         (against-background
           (fetch {:request "one"}) => frontpage-json
           (get-next-frame anything) => anything)

         (->>  {:request "forever"}
              get-frames (take 100) (drop 1) (map :response)) => 
         (n-of {:content_html "response"} 99)
         (provided
           (get-next-frame anything) => {:response {:content_html "response"}
                                         :request "/browse"}
           (fetch {:request "forever"}) => {:body "response"})

         (->> {:request "one"}
              get-frames (take 100) count) => 1
         (provided
           (get-next-frame anything) => nil
           (fetch {:request "one"}) => {:body "response" 
                                                  :request nil})
         )

(facts "About `zip-acc-vals`"
       (zip-acc-vals {:followers 9 :thumbs-url ["1" "2"] :durations [1 2]}) => [{:request "1" 
                                                                               :duration 1}
                                                                              {:request "2"
                                                                               :duration 2}])

(facts "About `make-accs-granular`"
       (vec (make-accs-granular [{:followers 9 :thumbs-url ["1"] :durations [1]}
                                 {:thumbs-url ["11" "22"] :durations [11 22]}])) =>
       [{:followers 9} 
        {:request "1" :duration 1}
        {:request "11" :duration 11}
        {:request "22" :duration 22}])


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;video

(facts "About `get-video`"
       (get-video {:no-url "stuff"}) => {:no-url "stuff" :response nil}
       (get-video {:video-url "url" :duration "0:01"}) => {:video-url "url" :duration "0:01" :response "body"}
       (provided
         (fetch anything) => "body"))

(facts "About `get-date`"
       (get-date (:body video-json)) => "May132013"
       )

(facts "About `get-likes`"
       (get-likes (:body video-json)) => "3268"
       )

(facts "About `get-dislikes`"
       (get-dislikes (:body video-json)) => "119"
       )

(facts "About `get-views`"
       (get-views (:body video-json)) => "1170768"
       )

(facts "About `scrap-video`"
       (scrap-video {:response "no-body"}) => {:response "no-body"}
       (scrap-video {:response video-json}) => (contains (dissoc sentiments :followers)))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;integration

(facts "About `get-user-accs`"
       (-> anything get-user-accs first ) =>  (contains
                                                {:response video-json :date anything
                                                 :likes "3268" :dislikes "119" :views "1170768"}) 
       (provided
         (get-video anything) => {:response video-json}
         (get-frames anything) => [1]
         (make-accs-granular anything) => [1])

       (get-user-accs anything) => [{:followers 0 :response nil}]
       (provided
         (get-frames anything) => [{:followers 0}])
       )

(facts "About `keep-2013_`"
       (keep-2013_ (reverse [{:date "May122001"} {:date "May132012"} {:date "May132013"} {:date "May132014"}
                             {:followers 0}])) =>
       (reverse [{:date "May132013"} {:date "May132014"} {:followers 0}]))


(facts "About `get-accs-stats`"

       (get-accs-stats [{:followers 0} {:likes 1} {:likes 2}]) => (contains #"date,followers" ",,,1," ",,,2,")
       )

(facts "About `get-user-stats`"
       (-> "user" get-user-stats) => (contains [#"date,followers.*\n.*,1\n.*,dislikes$"
                                                #",May132013,1170768,3268,119"])
       (provided
         (get-user-accs "user") => [{:followers 1} {:response video-json :date "May132013"
                                                    :likes "3268" :dislikes "119" :views "1170768"}])
       )


)

