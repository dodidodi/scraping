(ns scraping.youtube
  (:require 
    [clojure.string :as str]
    [scraping.helpers :as helpers]
    [cheshire.core :as json]
    [net.cgrand.enlive-html :as html]
    [clj-http.client :as client]
    [clojure.java.io :as io]
    [clj-time.core :as t]
    ))


(def header {"User-Agent" "Mozilla/5.0 (X11; Linux i586; rv:31.0) Gecko/20100101 Firefox/31.0"})
(def base-url "https://www.youtube.com/")


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;retrive data

(defn fetch-frontpage [user]
  (let [response (-> (str base-url "user/" user "/videos")
                     (client/get {:header header}))]
    {:body (-> response :body helpers/make-html-resource)}))

(defn fetch-next-frame [url]
  (let [response (-> (str base-url url)
                         (client/get {:header header})
                         :body (json/parse-string true))]
        {:content_html (-> response :content_html helpers/make-html-resource)
         :load_more_widget_html (-> response :load_more_widget_html helpers/make-html-resource)}))

(defn fetch-video [url]
  {:body (-> (str base-url url)
             (client/get {:header header})
             :body
             java.io.StringReader. html/html-resource)})

;(defn fetch [{{user :user video-url :video-url next-frame-url :next-frame-url} :request}]
(defn fetch [{request :request}]
  (do (Thread/sleep 5000)
      (condp re-find (str request)
        #"^/watch" (fetch-video request)
        #"^/browse" (fetch-next-frame request)
        #"^\w" (fetch-frontpage request)
        nil)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn get-followers [content]
  (helpers/scrap-first-match content
                             [:div#c4-primary-header-contents :div :span
                              :span.yt-subscription-button-subscriber-count-branded-horizontal.yt-uix-tooltip]))

(defn get-ajax-url [content]
  (let [css-sel [:button.load-more-button]]
    (-> content 
        (html/select css-sel) 
        first :attrs :data-uix-load-more-href)))

(defn get-next-frame [acc]
  (let [next-frame (fetch acc)]
    {:response next-frame
     :request (get-ajax-url (:load_more_widget_html next-frame))}))

(defn get-frames [acc]
  (->> acc
       fetch
       (#(array-map :response % 
                    :request (-> % :body get-ajax-url)
                    :followers (-> % :body get-followers)))
       (iterate get-next-frame)
       (take-while #(:response %))))

(defn get-thumbs-duration [content]
  (map html/text
       (html/select content
                    [:span.video-time :span])))

(defn get-thumbs-url [content]
  (map #(get-in % [:attrs :href])
       (html/select content
                    [:a.yt-uix-sessionlink.yt-uix-tile-link.spf-link.yt-ui-ellipsis.yt-ui-ellipsis-2])))

(defn get-body [acc]
  (let [{{content_html :content_html body :body} :response} acc]
    (if body body content_html)))

(defn scrap-frame [acc]
  (if-let [content (get-body acc)]
    (assoc acc
           :thumbs-url (get-thumbs-url content)
           :durations (get-thumbs-duration content))
    acc))

(defn zip-acc-vals [acc]
  (map #(array-map :request %1
                   :duration %2)
       (:thumbs-url acc) (:durations acc)))

(defn make-accs-granular [frames]
  (let [[hd & tl] frames]
    (cons {:followers (:followers hd)}
          (lazy-cat (zip-acc-vals hd)
                (->> tl (map zip-acc-vals) helpers/lazy-flatten1)))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;retrieve video stats

(defn get-video [acc]
  (assoc acc :response (fetch acc)))

(defn get-date [content]
  (helpers/scrap-first-match content 
                 [:div#watch-uploader-info :strong.watch-time-text]
                 #(str/replace % #"Publishedon" "")))

(defn get-views [content]
  (helpers/scrap-first-match content
                 [:div#watch7-views-info :div.watch-view-count]
                 helpers/filter-digits))

(defn get-likes [content]
  (helpers/scrap-first-match content
                 [:div#watch8-sentiment-actions :button.like-button-renderer-like-button :span]
                 helpers/filter-digits))

(defn get-dislikes [content]
  (helpers/scrap-first-match content
                 [:div#watch8-sentiment-actions :button.like-button-renderer-dislike-button :span]
                 helpers/filter-digits))

(defn scrap-video [acc]
  (if-let [content (-> acc :response :body)]
    (assoc acc 
           :date (get-date content)
           :likes (get-likes content)
           :dislikes (get-dislikes content)
           :views (get-views content))
    acc))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;aggregation 

(defn get-user-accs [user]
  (->> {:request user}
       get-frames
       (map scrap-frame)
       make-accs-granular
       (map get-video)
       (map scrap-video)
       ))

(defn keep-2013_ [[acc0 & accs]]
  (cons acc0  ;;leading info: followers and scrap date
        ;;assuming thumbs flow are time decreasing
        (take-while #(->> % :date (re-find #"\d{4}$") read-string (< 2012))
                    accs)))

(defn get-accs-stats [accs]
  (map (fn [acc] (cond (:error acc) (:error acc)
                       (:followers acc) (str "date,followers" "\n"
                                             (new java.util.Date) "," (:followers acc) "\n"
                                             "url,date,views,likes,dislikes")
                       :else (str/join "," 
                                       (map #(% acc) [:video-url :date :views :likes :dislikes]))))
       accs))

(defn get-user-stats [user]
  (-> user get-user-accs keep-2013_ get-accs-stats))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

