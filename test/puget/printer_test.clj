(ns puget.printer-test
  (:require
    [clojure.string :as str]
    [clojure.test :refer :all]
    (puget
      [data :refer [TaggedValue]]
      [printer :refer :all])))


(deftest color-scheme-setting
  (let [old-scheme (:color-scheme *options*)]
    (set-color-scheme! {:tag [:green]})
    (is (= [:green] (:tag (:color-scheme *options*))))
    (set-color-scheme! :nil [:black] :number [:bold :cyan])
    (is (= [:black] (:nil (:color-scheme *options*))))
    (is (= [:bold :cyan] (:number (:color-scheme *options*))))
    (set-color-scheme! old-scheme)))


(deftest map-delimiter-setting
  (let [old-delim (:map-delimiter *options*)]
    (use-map-commas!)
    (is (= "," (:map-delimiter *options*)))
    (alter-var-root #'*options* assoc :map-delimiter old-delim)))


(deftest canonical-primitives
  (testing "Primitive values"
    (are [v text] (= text (-> v pprint with-out-str str/trim))
         nil     "nil"
         true    "true"
         false   "false"
         0       "0"
         1234N   "1234N"
         2.718   "2.718"
         3.14M   "3.14M"
         3/10    "3/10"
         \a      "\\a"
         \space  "\\space"
         "foo"   "\"foo\""
         :key    ":key"
         :ns/key ":ns/key"
         'sym    "sym"
         'ns/sym "ns/sym")))


(deftest canonical-collections
  (testing "Collections"
    (are [v text] (= text (pprint-str v))
         '(foo :bar)            "(foo :bar)"
         '(1 2 3)               "(1 2 3)"
         [4 "five" 6.0]         "[4 \"five\" 6.0]"
         {:foo 8 :bar 'baz}     "{:bar baz :foo 8}" ; gets sorted
         #{:omega :alpha :beta} "#{:alpha :beta :omega}"))) ; also sorted


(defrecord TestRecord [foo bar])

(deftest canonical-records
  (testing "Records"
    (let [r (->TestRecord \x \y)]
      (with-strict-mode
        (is (thrown? IllegalArgumentException (pprint r))
            "should not print non-EDN representation"))
      (is (= "#puget.printer_test.TestRecord{:bar \\y :foo \\x}\n"
             (with-out-str (pprint r)))))))


(deftest clojure-types
  (testing "regex"
    (let [v #"\d+"]
      (with-strict-mode
        (is (thrown? IllegalArgumentException (pprint v))
            "should not print non-EDN representation"))
      (is (= "#\"\\d+\"" (pprint-str v)))))
  (testing "vars"
    (let [v #'TaggedValue]
      (with-strict-mode
        (is (thrown? IllegalArgumentException (pprint v))
            "should not print non-EDN representation"))
      (is (= "#'puget.data/TaggedValue"
             (pprint-str v)))))
  (testing "atom"
    (let [v (atom :foo)]
      (with-strict-mode
        (is (thrown? IllegalArgumentException (pprint v))
            "should not print non-EDN representation"))
      (is (re-seq #"#<Atom@[0-9a-f]+ :foo>" (pprint-str v)))))
  (testing "delay"
    (let [v (delay (+ 8 14))]
      (with-strict-mode
        (is (thrown? IllegalArgumentException (pprint v))
            "should not print non-EDN representation"))
      (is (re-seq #"#<Delay@[0-9a-f]+ pending>" (pprint-str v)))
      (is (= 22 @v))
      (is (re-seq #"#<Delay@[0-9a-f]+ 22>" (pprint-str v)))))
  (testing "future"
    (let [v (future (do (Thread/sleep 100) :done))]
      (with-strict-mode
        (is (thrown? IllegalArgumentException (pprint v))
            "should not print non-EDN representation"))
      (is (re-seq #"#<Future@[0-9a-f]+ pending>" (pprint-str v)))
      (is (= :done @v))
      (is (re-seq #"#<Future@[0-9a-f]+ :done>" (pprint-str v))))))


(deftest canonical-tagged-value
  (let [tval (reify TaggedValue
               (edn-tag [this] 'foo)
               (edn-value [this] :bar/baz))
        doc (canonize tval)]))


(deftest default-canonize
  (testing "Unknown values"
    (let [usd (java.util.Currency/getInstance "USD")]
      (with-strict-mode
        (is (thrown? IllegalArgumentException
                     (pprint usd))
                     "should not print non-EDN representation"))
      (is (= "#<java.util.Currency USD>" (pprint-str usd))))))


(deftest metadata-printing
  (let [value ^:foo [:bar]]
    (binding [*print-meta* true]
      (is (= "^{:foo true}\n[:bar]" (pprint-str value)))
      (is (= "[:bar]" (pprint-str value {:print-meta false}))))))


(deftest colored-printing
  (let [value [nil 1.0 true "foo" :bar]
        bw-str (with-out-str (pprint value))
        colored-str (with-out-str (cprint value))
        thin-str (cprint-str value {:width 5})]
    (is (> (count colored-str) (count bw-str)))
    (is (not= colored-str thin-str))
    (is (= "123" (with-color (color-text :frobble "123"))))
    (is (= "#{:baz}" (pprint-str #{:baz})))
    (is (= (cprint-str :foo)
           (with-color (color-text :keyword ":foo"))))))
