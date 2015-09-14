(ns clj.twitter
  (:require 
    [clj.helpers :refer :all]
    [clojure.string :as str]
    [clojure.java.io :as io]
    [cheshire.core :as json]
    [clj-fuzzy.metrics :as metrics])
  (:use
    [twitter.oauth]
    [twitter.callbacks]
    [twitter.callbacks.handlers]
    [twitter.api.restful])
  (:import
   (twitter.callbacks.protocols SyncSingleCallback) 
  ))


(def my-creds (let [auth (json/parse-string (slurp (io/resource "twitter.auth.json")) true)]
                (make-oauth-creds (:CONSUMER_KEY auth)
                                  (:CONSUMER_SECRET auth)
                                  (:OAUTH_TOKEN auth)
                                  (:OAUTH_TOKEN_SECRET auth))))

(defn params
  ([screen-name max-id]
   {:screen-name screen-name :count 200 :max-id max-id
    :exclude-replies "true" :include-rts "false"})
  ([screen-name]
   {:screen-name screen-name :count 200
    :exclude-replies "true" :include-rts "false"}))

(defn get-user-timeline
  ([screen-name]
   (statuses-user-timeline :oauth-creds my-creds
                           :params (params screen-name)
                           :callbacks (SyncSingleCallback. response-return-body
                                                           response-throw-error
                                                           exception-rethrow)))
  ([screen-name max-id]
   (statuses-user-timeline :oauth-creds my-creds
                           :params (params screen-name max-id)
                           :callbacks (SyncSingleCallback. response-return-body
                                                           response-throw-error
                                                           exception-rethrow)))) 

(defn get-next-batch [screen-name prev-batch]
  (if-let [max-id (get (last prev-batch) :id)]
    (get-user-timeline screen-name max-id)))

(defn go2nxt16-batches [screen-name [i curr-batch]]
  (if (< i 15)
    [(inc i) (get-next-batch screen-name curr-batch)]))

(defn get-tweets [screen-name]
  (let [timeline0 (get-user-timeline screen-name)
        timelines (map last
                       (take-while identity
                                   (iterate (partial go2nxt16-batches screen-name)
                                            [0 timeline0])))]
    (for [batch timelines, timeline batch]
      timeline)))

(defn get-path-out [pwd screen-name]
  (str pwd screen-name "_twitter.json"))
