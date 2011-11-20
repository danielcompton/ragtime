(ns ragtime.core
  "Functions and macros for defining and applying migrations."
  (:use [clojure.core.incubator :only (-?>)]
        [clojure.tools.macro :only (name-with-attributes)]))

(defprotocol Migration
  "The migration protocol is meant to be reified into a single migration
  unit. See the defmigration macro."
  (id [migration] "A unique identifier for the migration")
  (up [migration] "Apply the migration to the database")
  (down [migration] "Rollback the migration"))

(defmacro migration
  "Creates a new migration with the supplied id and clauses. The id must be
  unique. The clauses consist of an :up form, which is evaluated when the
  migration is applied, and a :down form, which is evalidated when the migration
  is rolled back.

  (migration \"a-unique-id\"
     (:up (apply-migration))
     (:down (rollback-migration))"
  [id & clauses]
  (let [methods (reduce
                 (fn [m [k & body]] (assoc m k body))
                 {}
                 clauses)]
    `(reify Migration
       (id [_] (name ~id))
       (up [_] ~@(:up methods))
       (down [_] ~@(:down methods)))))

(defonce defined-migrations (atom {}))

(defmacro defmigration
  "Defines a migration in the current namespace. See ragtime.core/migration.

  (defmigration a-migration
    (:up (apply-migration))
    (:down (rollback-migration)))"
  [name & args]
  (let [[name args] (name-with-attributes name args)]
    `(let [id# (str (ns-name *ns*) "/" ~(str name))]
       (def ~name (migration id# ~@args))
       (defonce ~'db-migrations (atom []))
       (swap! ~'db-migrations conj ~name)
       (swap! defined-migrations assoc id# ~name))))

(defn- namespace-name [ns]
  (if (instance? clojure.lang.Namespace ns)
    (name (ns-name ns))
    (name ns)))

(defn- migrations-ref [namespace]
  (-?> (namespace-name namespace)
       (symbol "db-migrations")
       (find-var)
       (var-get)))

(defn list-migrations
  "Lists the migrations in a namespace in the order in which they were defined."
  [namespace]
  (-?> (migrations-ref namespace)
       (deref)))

(defn reset-migrations!
  "Clears the migrations from an existing namespace."
  [namespace]
  (-?> (migrations-ref namespace)
       (reset! [])))
