(ns ccw-plugin-eastwood
  (:require [ccw.api.markers      :as ma])
  (:require [ccw.eclipse          :as e])
  (:require [ccw.leiningen.launch :as l])
  (:require [clojure.string       :as str])
  (:require [clojure.java.io      :as io])
  (:require [ccw.e4.dsl           :refer :all]))

(ma/register-marker-type!
  {:id "ccw-plugin-eastwood", :name "Eastwood Linter", :persistent true})

(defn read-directory
  "directory entry format: Entering directory `<directory-path>'
   return a String for the directory-path, or nil"
  [s]
  (second (re-find #"Entering directory `(.*)'" s)))

(defn read-hint
  "hint format: <file>:<line>:<col>: <linter> <msg>
   return a {:file :line :col :linter+msg} map, or nil"
  [directory s]
  (let [[file line col linter+msg] (str/split s #":" 4)]
    (when linter+msg
      {:file (.getAbsolutePath (io/file directory file))
       :line (Integer/parseInt line)
       :col  (Integer/parseInt col)
       :linter+msg linter+msg})))

(defn read-hints
  "Read all hints at once, from the result of having called Eastwood"
  [s]
  (:hints (reduce
            (fn [{:keys [directory] :as c} s]
              (condp apply [s]
                (partial read-hint directory) :>> #(update-in c [:hints] conj %)
                read-directory                :>> #(assoc c :directory %)
                c))
            {:directory nil :hints []}
            (str/split-lines s))))

(defn create-eastwood-marker!
  "Create an Eclipse Marker corresponding to the given hint"
  [hint]
  (ma/create-marker!
    (e/resource (:file hint))
    {:type     "ccw-plugin-eastwood"
     :severity :warning
     :line-number (:line hint)
     :message (:linter+msg hint)}))

(defn result-listener
  "Listener that receives the String corresponding to stdout, stderr, and the exit code
   of a terminated process.
   Gather all the hints, the associated projects, remove markers for those projects,
   then add markers for the hints."
  [str-out str-err exit-code]
  (let [hints (read-hints str-out)
        projects (->> hints (map :file) (map e/resource) (map e/project) distinct)]
    (doseq [p projects] (ma/delete-markers! p))
    (doseq [hint hints] (create-eastwood-marker! hint))))

(defcommand refresh-eastwood
  "Refresh Eastwood Linter markers"
  "Cmd+U E"
  [{:keys [active-selection active-part]}]
  (when-let [project (e/project active-selection)] ; TODO work from within editor
    (future (e/ui (l/lein project, "eastwood", :result-listener result-listener)))))
