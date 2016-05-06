(ns morpheus.traversal.dfs
  (:require [morpheus.messaging.core :as msg]
            [morpheus.models.edge.core :as edges]
            [morpheus.models.vertex.core :as vertex]
            [neb.base :as nb]
            [clojure.core.async :as a]
            [neb.core :as neb]
            [cluster-connector.utils.for-debug :refer [$ spy]])
  (:import (java.util.concurrent TimeoutException)))

;; Distributed deepeth first search divised by S.A.M. Makki and George Havas

;; Message schama [vertex-id stack filter max-deepth]

(def pending-tasks (atom {}))

(defn send-stack [task-id action vertex-id stack {:keys [filter max-deepth]}]
  (let [server-name (nb/locate-cell-by-id vertex-id)]
    (case action
      :DFS-FORWARD (msg/send-msg server-name action [vertex-id stack filter max-deepth] :task-id task-id)
      :DFS-RETURN  (msg/send-msg server-name action stack                               :task-id task-id))))

(defn proc-forward-msg [task-id data]
  (let [[vertex-id stack filters max-deepth] data
        neighbours (apply edges/neighbours (vertex/get-veterx-by-id vertex-id) (or filters []))
        current-vertex-stat (atom nil)
        proced-stack (doall (map (fn [v]
                                   (let [[svid] v]
                                     (if (= svid vertex-id)
                                       (do (reset! current-vertex-stat v)
                                           (assoc v 1 1) ;; reset flag to visited
                                           )
                                       v)))
                                 stack))
        deepth (@current-vertex-stat 2)
        next-depth (inc deepth)
        stack-verteics (set (map first stack))
        neighbour-oppisites (->> (map (fn [{:keys [*start* *end*]}]
                                        (let [opptsite-id (cond (and (= vertex-id *start*) (not= vertex-id *end*))   *end*
                                                                (and (= vertex-id *end*)   (not= vertex-id *start*)) *start*)]
                                          (when (and opptsite-id (not (stack-verteics opptsite-id)))
                                            [opptsite-id 0 next-depth vertex-id])))
                                      neighbours)
                                 (filter identity))
        final-stack (if-not (> deepth (or max-deepth Long/MAX_VALUE))
                      (concat neighbour-oppisites proced-stack)
                      proced-stack)
        unvisited-id (first (first (filter (fn [[_ flag]] (= flag 0)) final-stack)))
        all-visted? (nil? unvisited-id)]
    (if all-visted?
      (let [root-id (first (last stack))]
        (send-stack task-id :DFS-RETURN root-id proced-stack {}))
      (send-stack task-id :DFS-FORWARD unvisited-id final-stack {:filter filters :max-deepth max-deepth}))))

(defn proc-return-msg [task-id data]
  (let [feedback-chan (get @pending-tasks task-id)]
    (a/>!! feedback-chan data)))

(defn dfs [vertex & {:keys [filter max-deepeth timeout] :as extra-params
                        :or {timeout 60000}}]
  (let [task-id (neb/rand-cell-id)
        vertex-id (:*id* vertex)
        feedback-chan (a/chan 1)]
    (swap! pending-tasks assoc task-id feedback-chan)
    ;;                                          [svid      flag depth parent]
    (send-stack task-id :DFS-FORWARD vertex-id [[vertex-id 0    0     nil]]
                (dissoc extra-params :timeout))
    (let [feedback (first (a/alts!! [(a/timeout timeout) feedback-chan]))]
      (swap! pending-tasks dissoc task-id feedback-chan)
      (a/close! feedback-chan)
      (if (nil? feedback)
        (throw (TimeoutException.))
        (map  feedback)))))

(msg/register-action :DFS-FORWARD proc-forward-msg)
(msg/register-action :DFS-RETURN  proc-return-msg)