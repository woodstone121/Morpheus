(ns morpheus.tests.traversal
  (:require [midje.sweet :refer :all]
            [morpheus.tests.server :refer [with-server]]
            [morpheus.models.vertex.core :refer :all]
            [morpheus.models.edge.core :refer :all]
            [morpheus.traversal.dfs :as dfs]
            [morpheus.traversal.bfs :as bfs]
            [cluster-connector.utils.for-debug :refer [$ spy]]))

(facts
  "Traversal Tests"
  (with-server
    (let [get-vertex1 (fn [n] (vertex-by-key :item-1 (str n)))
          get-vertex2 (fn [n] (vertex-by-key :item-2 (str n)))]
      (fact "Schemas"
            (new-vertex-group! :item-1 {:body :dynamic :key-field :name}) => anything
            (new-vertex-group! :item-2 {:body :dynamic :key-field :name}) => anything
            (new-edge-group! :link1 {:type :indirected :body :dynamic}) => anything
            (new-edge-group! :link2 {:type :indirected :body :dynamic}) => anything)
      (fact "Create Edges"
            (new-vertex! :item-1 {:name "1"}) => anything
            (new-vertex! :item-1 {:name "2"}) => anything
            (new-vertex! :item-1 {:name "3"}) => anything
            (new-vertex! :item-1 {:name "4"}) => anything
            (new-vertex! :item-1 {:name "5"}) => anything
            (new-vertex! :item-1 {:name "6"}) => anything
            (new-vertex! :item-1 {:name "7"}) => anything
            (new-vertex! :item-1 {:name "8"}) => anything
            (new-vertex! :item-1 {:name "9"}) => anything
            (new-vertex! :item-1 {:name "10"}) => anything
            (new-vertex! :item-2 {:name "11"}) => anything
            (new-vertex! :item-2 {:name "12"}) => anything
            (new-vertex! :item-2 {:name "13"}) => anything
            (new-vertex! :item-2 {:name "14"}) => anything
            (new-vertex! :item-2 {:name "15"}) => anything
            (new-vertex! :item-2 {:name "16"}) => anything
            (new-vertex! :item-2 {:name "17"}) => anything
            (new-vertex! :item-2 {:name "18"}) => anything
            (new-vertex! :item-2 {:name "19"}) => anything
            (new-vertex! :item-2 {:name "20"}) => anything
            (new-vertex! :item-2 {:name "21"}) => anything
            (new-vertex! :item-2 {:name "22"}) => anything)
      (fact "Create Network"

            ;;   1  - 2  - 3  - 4  - 5
            ;;             |
            ;;   6  - 7  - 8  - 9  - 10
            ;;   |                    |
            ;;   11 - 12 - 13 - 14 - 15
            ;;
            ;;   16 - 17 - 18 - 19 - 20
            ;;   |                   |
            ;;   21                  22

            (fact "Sub Graph 1"
                  (link! (get-vertex1 1) :link1 (get-vertex1 2))  => anything
                  (link! (get-vertex1 2) :link1 (get-vertex1 3))  => anything
                  (link! (get-vertex1 3) :link1 (get-vertex1 4))  => anything
                  (link! (get-vertex1 4) :link1 (get-vertex1 5))  => anything
                  (link! (get-vertex1 6) :link1 (get-vertex1 7))  => anything
                  (link! (get-vertex1 7) :link1 (get-vertex1 8))  => anything
                  (link! (get-vertex1 8) :link1 (get-vertex1 9))  => anything
                  (link! (get-vertex1 9) :link1 (get-vertex1 10)) => anything
                  (link! (get-vertex1 3) :link1 (get-vertex1 8))  => anything

                  (link! (get-vertex2 11) :link2 (get-vertex2 12))  => anything
                  (link! (get-vertex2 12) :link2 (get-vertex2 13))  => anything
                  (link! (get-vertex2 13) :link2 (get-vertex2 14))  => anything
                  (link! (get-vertex2 14) :link2 (get-vertex2 15))  => anything

                  (link! (get-vertex1 6)  :link2 (get-vertex2 11))  => anything
                  (link! (get-vertex1 10) :link2 (get-vertex2 15))  => anything)

            (fact "Sub Graph 2"
                  (link! (get-vertex2 16) :link1 (get-vertex2 17))  => anything
                  (link! (get-vertex2 17) :link1 (get-vertex2 18))  => anything
                  (link! (get-vertex2 18) :link1 (get-vertex2 19))  => anything
                  (link! (get-vertex2 19) :link1 (get-vertex2 20))  => anything
                  (link! (get-vertex2 16) :link1 (get-vertex2 21))  => anything
                  (link! (get-vertex2 20) :link1 (get-vertex2 22))  => anything))
      (fact "Simple check"
            (degree (get-vertex1 1)) => 1
            (count (apply neighbours (get-vertex1 1) [])) => 1)
      (fact "DFS"
            (fact "Subgraph 1 search"
                  (println "Starting DFS")
                  (let [dfs-outout (:stack (dfs/dfs (get-vertex1 1)))
                        subgraph-1 (concat (map (fn [i] (vertex-id-by-key :item-1 (str i))) (range 1 11))
                                           (map (fn [i] (vertex-id-by-key :item-2 (str i))) (range 11 16))) ]
                    (println "DFS Test End")
                    (count dfs-outout) => 15
                    (map :id dfs-outout) => (just subgraph-1 :in-any-order)))
            (fact "Subgraph 1 search with edge restriction"
                  (count (:stack (dfs/dfs (get-vertex1 1) :filters {:type :link1}))) => 10)
            (fact "Has Path"
                  (dfs/has-path? (get-vertex1 1) (get-vertex2 15)) => truthy
                  (dfs/has-path? (get-vertex1 1) (get-vertex2 22)) => falsey
                  )
            (fact "Adjacency list"
                  (dfs/adjacancy-list (get-vertex1 1)) => #(> (count %) 0))
            (fact "Path to"
                  (dfs/path-to (get-vertex1 1) (get-vertex2 15) :with-vertices? true) => #(= 2 (count %))
                  (dfs/one-path-to (get-vertex1 1) (get-vertex1 3)) => (contains [(contains {:deepth 0 :parent nil})
                                                                              (contains {:deepth 1})
                                                                              (contains {:deepth 2})])))
      (fact "BFS"
            (fact "Create Edge"

                  ;;   1  - 2  - 3  - 4  - 5
                  ;;   +         |
                  ;;   6  - 7  - 8  - 9  - 10
                  ;;   |                    |
                  ;;   11 - 12 - 13 - 14 - 15
                  ;;
                  ;;   16 - 17 - 18 - 19 - 20
                  ;;   |                   |
                  ;;   21                  22

                  (link! (get-vertex1 1) :link1 (get-vertex1 6))  => anything)
            (println "Starting BFS")
            (fact "Subgraph 1 search"
                  (let [bfs-output (bfs/bfs (get-vertex1 1))]
                    (println "BFS Test End")
                    (count bfs-output) => 15
                    (count (bfs/bfs (get-vertex1 1) :max-deepth 1)) => 3
                    (count (bfs/bfs (get-vertex1 1) :max-deepth 2)) => 6

                    (set (map (comp read-string :name)
                              (bfs/bfs (get-vertex1 1)
                                       :max-deepth 2
                                       :with-vertices? true))) => #{1 2 3 6 7 11}
                    (count (bfs/shortest-path (get-vertex1 1) (get-vertex1 8) :max-deepth 50)) => 2
                    (count (bfs/shortest-path (get-vertex1 8) (get-vertex2 11) :max-deepth 50)) => 1
                    (count (bfs/shortest-path (get-vertex1 8) (get-vertex2 13) :max-deepth 50)) => 2
                    (bfs/has-path? (get-vertex1 1) (get-vertex2 13)) => truthy
                    (bfs/has-path? (get-vertex1 1) (get-vertex2 20)) => falsey
                    (bfs/has-path? (get-vertex1 1) (get-vertex2 20) :on-disk? true) => falsey
                    (bfs/has-path? (get-vertex1 1) (get-vertex2 13) :on-disk? true) => truthy))))))