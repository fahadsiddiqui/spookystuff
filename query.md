---
layout: global
title: Query - SpookyStuff SPOOKYSTUFF_VERSION documentation
---

* This will become a table of contents (this text will be scraped).
{:toc}

# Overview

SpookyStuff's' Query Language is a short and powerful LINQ (Language-Integrated Query) that abstracts away the complexity of unstructured document parsing/traversing, link crawling and parallel computing/optimization, leaving only two things for you to consider: The data you want and the process to discover them. This leads to our first rule in syntax design: identical queries should expect identical results, and vice versa.

SpookyStuff is built on Scala, it borrows some concepts and most of its syntax from [Spark SQL] (e.g. inner & left join, explode, alias and select), notably its own [Language-Integrated Query]. This is for easier understanding and memorizing by SQL users, rather than draw an analogy between relational databases and linked/unstructured datasets. In fact, they have very different specifications: In linked data, join by expression and filter by predicate ("WHERE" clause in SQL) are prohibitively expensive if executed on the client side, which makes URI and web service-based lookup its only standard.

The following diagram illustrates the elements of SpookyStuff queries: context, clauses, actions and expressions.

![img]

To run it in any Scala environment, import all members of the package org.tribbloid.spookystuff.dsl:

    import org.tribbloid.spookystuff.dsl._

# Context

SpookyContext is the entry point of all queries, it can be constructed from a [SparkContext] or [SQLContext], with SpookyConf as an optional parameter:

    val spooky = new SpookyContext(sc: SparkContext) // OR
    val spooky = new SpookyContext(sql: SQLContext) // OR
    val spooky = new SpookyContext(sql: SQLContext, conf: SpookyConf)

SpookyConf contains configuration options that are enumerated in [Configuration Section], these won't affect the result of the query, but have an impact on execution efficiency and resiliency. all these options can be set statically and changed dynamically, even in the middle of a query, e.g.

    import scala.concurrent.duration.Duration._

    spooky.conf.pageExpireAfter = 1.day // OR
    spooky.<clauses>.setConf(_.pageExpireAfter = 1.day).<clauses>.... // in the middle of a query

Multiple SpookyContext can co-exist and their configurations will only affect their derived queries respectively.

# Clauses

Clauses are building blocks of a query, each denotes a specific pattern to discover new data from old data's references (e.g. through hyperlinks). Clauses can be applied to any of the following Scala objects:

- PageRowRDD: this is the default data storage optimized for unstructured and linked data operations, like DataFrame optimized for relation data. Internally, PageRowRDD is a distributed and immutable row-storage that contains both unstructured "raw" documents and key-value pairs. it can be created from a compatible Spark dataset type by using SpookyContext.create():

        val frame2 = spooky.create(Map("name" -> "Cloudera", "type" -> "company") :: Map("name" -> "Hadoop", "type" -> "Software") :: Nil) //create a PageRowRDD with 2 fields: 'name and 'type
        val frame1 = spooky.create(1 to 10) //create a PageRowRDD of 10 rows //if source only contains 1 datum per row and its field name is missing, create a PageRowRDD with 1 field: '_

And to those types by the following functions:

        frame1.toStringRDD('_)  // convert one field to RDD[String]
        frame2.toMapRDD()       // convert to MapRDD
        frame2.toDF()           // convert to DataFrame
        frame2.toJSON()         // equal to .toDF().JSON()
        frame2.toCSV()          // convert to CSV
        frame2.toTSV()          // convert to TSV

PageRowRDD is also the output of another clause so you can chain multiple clauses easily to form a long data valorization pipeline. In addition, it inherits SpookyContext from its source or predecessor, so settings in SpookyConf are persisted for all queries derived from them (unless you change them halfway, this is common if a query uses mutliple web services, each with its own Authentication credential or proxy requirement).

- SpookyContext: equivalent to applying to an empty PageRowRDD with one row.

- Any compatible Spark dataset type: equivalent to applying to a PageRowRDD initialized from SpookyContext.create(). To use this syntax you need to import context-aware implicit conversions:

        import spooky.dsl._ //

The following 5 main clauses covered most data reference patterns in websites, documents and APIs, including, but not limited to, one-to-one, one-to-many, link graph and paginations.

#### fetch

Syntax:

    <Source>.fetch(<Action(s)>, <parameters>)

Fetch is used to remotely fetch unstructured document(s) per row according to provided actions and load them into each row's document buffer, which flush out previous document(s). Fetch can be used to retrieve an URI directly or combine data from different sources based on one-to-one relationship, e.g.:

    spooky.fetch(Wget("https://www.wikipedia.com")).toStringRDD(S.text).collect().foreach(println) // this loads the landing page of Wikipedia into a PageRowRDD with cardinality 1.

    spooky.create(1 to 20).fetch(Wget("https://www.wikidata.org/wiki/Q'{_}")).flatMap(S.text).collect().foreach(println) //this loads 20 wikidata pages, 1 page per row

Where ```Wget``` is a simple [action] that retrieves a document from a specified URI. Notice that the second URI is incomplete: part of it is denoted by ```'{_}'```, this is a shortcut for string interpolation: during execution, any part enclosed in ```'{<key>}'``` will be replaced by a value in the same row the <key> identifies. String interpolation is part of a rich expression system covered in [Expression Section].

Parameters:It should be noted that m

| Name | Default | Means |
| ---- | ------- | ----- |
| joinType |
| flattenPagesPattern |
| flattenPagesOrdinalKey |
| numPartitions |
| optimizer |

#### select/remove

Syntax:

    <Source>.select(
        <expression> [~ '<alias>],
        <expression> [~+ '<alias>],
        <expression> [~! '<alias>]
        ...
    )

Select is used to extract data from unstructured documents and persist into its key-value store, unlike SQL, select won't discard existing data from the store:

    spooky.fetch(
        Wget("https://en.wikipedia.com")
    ).select(S"table#mp-left p" ~ 'featured_article).toDF().collect().foreach(println) //this load the featured article text of Wikipedia(English) into one row

If the alias of an expression already exist in the store it will throw a QueryException and refuse to execute. In this case you can use ```~! <alias>``` to replace old value or ```~+ <alias>``` to append the value to existing ones as an array. To remove key-value pairs from PageRowRDD, use remove:

    <Source>.remove('<alias>, '<alias> ...)

#### flatten/explode

Syntax:

    <Source>.flatten/explode(<expression> [~ alias], <parameters>)

Flatten/Explode is used to transform each row into a sequence of rows, each has an object of the array selected from the original row:

    spooky.fetch(
        Wget("https://news.google.com/news?q=apple&output=rss")
    ).flatten(S"item title" ~ 'title).toDF().collect().foreach(println) //select and explode all titles in google RSS feed into multiple rows

Parameters:

| Name | Default | Means |
| ---- | ------- | ----- |
| joinType |

SpookyStuff has a shorthand for flatten + select, which is common for extracting multiple attributes and fields from elements in a list or tree:

    <Source>.flatSelect(<expression>)(
        <expression> [~ '<alias>]
        ...
    )

This is equivalent to:

    <Source>.flatten(<flatten expression> ~ 'A), <flatten parameters>
    ).select(
        <select expression> [~ '<alias>]
        ...
    )

e.g.:

    spooky.fetch(
        Wget("https://news.google.com/news?q=apple&output=rss")
    ).flatSelect(S"item")(
        A"title" ~ 'title
        A"pubDate" ~ 'date
    ).toDF().collect().foreach(println) //explode articles in google RSS feed and select titles and publishing date

You may notice that the first parameter of flatSelect has no alias - SpookyStuff can automatically assign a temporary alias 'A to this intermediate selector, which enables a few shorthand expressions for traversing deeper into objects being flattened. This "big A selector" will be used more often in join and explore.

#### join

Syntax:

    <Source>.join(<flatten expression> [~ alias], <flatten parameters>
    )(<Action(s)>, <fetch parameters>)(
        <select expression> [~ '<alias>]
        ...
    )

Join is used to horizontally combine data from different sources based on one-to-many/many-to-many relationship, e.g. combining links on a search/index page with fulltext contents:

    spooky.create("Gladiator" :: Nil).fetch(
        Wget("http://lookup.dbpedia.org/api/search/KeywordSearch?QueryClass=film&QueryString='{_}")
    ).join(S"Result")(
        Wget(A"URI".text),2
    )(
        A"Label".text ~ 'label,
        S"span[property=dbo:abstract]".text ~ 'abstract
    ).toDF().collect().foreach(println) //this search for movies named "Gladiator" on dbpedia Lookup API (http://wiki.dbpedia.org/projects/dbpedia-lookup) and link to their entity pages to extract their respective abstracts

Join is a shorthand for flatten/explode + fetch, in which case the data/elements being flatten is equivalent to a foreign key.

Parameters:

(See [flatten/explode parameters] and [fetch parameters])

#### explore

Syntax:

    <Source>.explore(<expression> [~ alias], <flatten parameters>, <explore parameters>
    )(<Action(s)>, <fetch parameters>)(
        <select expression> [~ '<alias>]
        ...
    )

Explore defines a parallel graph exploring pattern that can be best described as recursive join with deduplication: In each iteration, each row is joined with one or more documents, they are compared and merged with existing documents that has been traversed before, the iteration stops when maximum exploration depth has been reached, or no more new documents are found. Finally, all documents and their rows are concatenated vertically. Explore is used for conventional web crawling or to uncover "deep" connections in a graph of web resources, e.g. pagination, multi-tier taxonomy/disambiguation pages, "friend-of-a-friend" (FOAF) reference in a social/semantic network:

    spooky.fetch(
        Wget("http://dbpedia.org/page/Bob_Marley")
    ).explore(
        S"a[rel^=dbo],a[rev^=dbo]",
        maxDepth = 2
    )(Wget('A.href)
    )('A.text ~ 'name).toDF().collect().foreach(println) // this retrieve all documents that are related to (rel) or reverse-related to (rev) dbpedia page of Bob Marley

Parameters:

| Name | Default | Means |
| ---- | ------- | ----- |
| joinType |



#### etc.

# Actions

Each action defines a web client command, such as content download, loading a page in browser, or clicking a button.

Action can be chained to define a series of command in a client session, e.g:

    spooky.fetch(
        Visit("http://www.google.com/")
          +> TextInput("input[name=\"q\"]","Deep Learning")
          +> Submit("input[name=\"btnG\"]")
          +> Snapshot()
    ).flatMap(S.text).collect().foreach(println) //in a browser session, search "Deep Learning" on Google's landing page

In addition to the "chaining" operator **+>**, the following 2 can be used for branching:

- **||**: Combine multiple actions and/or chains in parallel, each executed in its own session and in parallel. This multiplies the cardinality of output with a fixed factor, e.g.:

    spooky.create("I'm feeling lucky"
    ).fetch(
        Wget("http://api.mymemory.translated.net/get?q='{_}&langpair=en|fr") ||
        Wget("http://api.mymemory.translated.net/get?q='{_}&langpair=en|de")
    ).select((S \ "responseData" \ "translatedText" text) ~ 'translated).toDF().collect().foreach(println) // load french and german translation of a sentence in 2 rows.

- **\*>**: Cross/Cartesian join and concatenate a set of actions/chains with another set of actions/chains, resulting in all their combinations being executed in parallel. e.g.:

    fetch(
        Visit("https://www.wikipedia.org/")
          +> TextInput("input#searchInput","Mont Blanc")
          *> DropDownSelect("select#searchLanguage","en") || DropDownSelect("select#searchLanguage","fr")
          +> Submit("input[type=submit]")
          +> Snapshot()
    ).flatMap(S.text).collect().foreach(println) // in 2 browser session, search "Mont Blanc" in English and French on Wikipedia's landing page

All out-of-the-box actions are categorized and listed in the following sections. parameters enclosed in square brackets are **[optional]** and are explained in [Optional Parameter Section]. If you are still not impressed and would like to write your own action, please refer to [Writing Extension Section].

#### Client & Thread

| Syntax | Means |
| ---- | ------- |
| Wget(<URI expression>, [<filter>]) [~ '<alias>] | retrieve a document by its universal resource identifier (URI), whether its local or remote and the client protocol is determined by schema of the URI, currently, supported schema/protocals are http, https, ftp, file, hdfs, s3, s3n and all Hadoop-compatible file systems.* |
| OAuthSign(<Wget>) | sign the http/https request initiated by Wget by OAuth, using credential provided in SpookyContext, not effective on other protocols |
| Delay([<delay>]) | hibernate the client session for a fixed duration |
| RandomDelay([<delay>, <max>]) | hibernate the client session for a random duration between <delay> and <max> |

* local resources (file, hdfs, s3, s3n and all Hadoop-compatible file systems) won't be cached: their URIs are obviously not "universal", plus fetching them directly is equally fast.

* http and https clients are subjective to proxy option in SpookyContext, see [Configuration Section] for detail.

* Wget is the most common action as well as the quickest. In many cases its the only action resolved in a fetch, join or explore, for which cases 3 shorthands clauses can be used:

        <Source>.wget(<URI>, <>)

        <Source>.wgetJoin(<selector>, <>)

        <Source>.wgetExplore(<>)

#### Browser

The following actions are resolved against a browser launched at the beginning of the session. The exact type and specifications (screen resolution, image/javascript supports) of the browser being launched and its proxy setting are subjective to their respective options in SpookyContext. Launching the browser is optional and necessity driven: the session won't launch anything if there is no need.

| Syntax | Means |
| ---- | ------- |
| Visit(<URI expression>, [<delay>, <blocking>]) | go to the website denoted by specified URI in the browser, usually performed by inserting the URI into the browser's address bar and press enter |
| Snapshot([<filter>]) [~ '<alias>] | export current document in the browser, the document is subjective to all DOM changes caused by preceding actions, exported documents will be loaded into the document buffer of the resulted PageRowRDD, if no SnapShot, Screenshot or Wget is defined in a chain, Snapshot will be automatically appended as the last action|
| Screenshot([<filter>]) [~ '<alias>] | export screenshot of the browser as an image document in PNG format |
| Assert([<filter>]) | Same as Snapshot, but this is only a dry-run which ensures that current document in the browser can pass the <filter>, nothing will be exported or cached |
| Click(<selector>, [<delay>, <blocking>]) | perform 1 mouse click on the 1st visible element that qualify the jQuery <selector> |
| ClickNext(<selector>, [<delay>, <blocking>]) | perform 1 mouse click on the 1st element that hasn't been clicked before within the session, each session keeps track of its own history of interactions and elements involved |
| Submit(<selector>, [<delay>, <blocking>]) |  submit the form denoted by the 1st element that qualify the jQuery <selector>, this is usually performed by clicking, but can also be done by other interactions, like pressing enter on a text box |
| TextInput(<selector>, <text expression>, [<delay>, <blocking>]) | focus on the 1st element (usually but not necessarily a text box) that qualify the jQuery <selector>, then insert/paste the specified text |
| DropDownSelect(<selector>, <text expression>, [<delay>, <blocking>]) | focus on the 1st selectable list that qualify the jQuery <selector>, then select the item with specified value |
| ExeScript(<script expression>, [<selector>, <delay>, <blocking>]) | run javascript program against the 1st element that qualify the jQuery <selector>, or against the whole document if <selector> is set to null or missing |
| DragSlider(<selector>, <percentage>, <handleSelector>, [<delay>, <blocking>]) | drag a slider handle denoted by <handleSelector> to a position defined by a specified percentage and an enclosing bounding box denoted by <selector> |
| WaitFor(<selector>) | wait until the 1st element that qualify the jQuery <selector> appears |

* Visit +> Snapshot is the second most common action that does pretty much the same thing as Wget on HTML resources - except that it can render dynamic pages and javascript. Very few formats other than HTML/XML are supported by browsers without plug-in so its mandatory to fetch images and PDF files by Wget only. In addition, its much slower than Wget for obvious reason. If Visit +> Snapshot is the only chained action resolved in a fetch, join or explore, 3 shorthand clauses can be used:

        <Source>.visit(<URI>, <>)

        <Source>.visitJoin(<selector>, <>)

        <Source>.visitExplore(<>)

* Multiple Snapshots and Screenshots can be defined in a chain of actions to persist different states of a browser session, their exported documents can be specifically referred by their aliases.

#### Flow Control

| Syntax | Means |
| ---- | ------- |
| Loop(<Action(s)>, [<limit>]) | Execute the specified <Action(s)> repeatedly until max iteration is reached or an exception is thrown during the execution (can be triggered by Assert) |
| Try(<Action(s)>, [<retries>, <cacheFailed>]) | By default, if an exception is thrown by an action, the entire session is retried, potentially by a different Spark executor on a different machine, this failover handling process continues until **spark.task.maxFailures** property has been reached, and the entire query failed fast. However, if the <Action(s)> throwing the exception is enclosed in the **Try** block, its last failure will be tolerated and only results in an error page being exported as a placeholder. Make sure to set **spark.task.maxFailures** to be larger than <retries>, or you query will fail fast before it even had a chance to interfere! |
| If(<condition>, <Action(s) if true>, <Action(s) if false>) | perform a condition check on current document in the browser, then execute different <Action(s)> based on its result |

* Loop(ClickNext +> Snapshot) is generally used for turning pages when its pagination is implemented in AJAX rather than direct hyperlinks, in which case a shorthand is available:

#### Optional Parameters

| Name | Default | Means |
| ---- | ------- | ----- |
| <filter> | org.tribbloid.spookystuff.dsl.ExportFilters.MustHaveTitle |
| <alias> | <same as Action> |
| <timeout> | null |
| <delay> | 1.second |
| <blocking> | true |
| <limit> | 500 |
| <retries> | 3 |
| <cacheError> | false |

# Expressions

In previous examples, you may already see some short expressions being used as clause or action parameters, like the [string interpolation] in URI templating, or the ["Big S"] / ["Big A"] selectors in many select clauses for unstructured information refinement. These are parts of a much more powerful (and equally short) expression system. It allows complex data reference to be defined in a few words and operators (words! not lines), while other frameworks may take scores of line. E.g. the following query uses an URI expression (in the second Wget), which pipes all titles on Google News RSS feed into a single MyMemory translation API call:

    spooky.fetch(Wget("https://news.google.com/?output=rss")
    ).fetch(Wget(

        //how short do you want it? considering its complexity
        x"http://api.mymemory.translated.net/get?q=${S"item title".texts.mkString(". ")}&langpair=en|fr"

    )).toStringRDD(S.text).collect().foreach(println)

Internally, each expression is a function from a single row in PageRowRDD to a nullable data container. Please refer to [Extension Section] if you want to use customized expressions.

The following is a list of basic symbols and functions/operators that are defined explicitly as shorthands:

#### symbols

Scala symbols (identifier preceded by tick/') has 2 meanings: it either returns a key-value pair by its key, or a subset of documents in the unstructured document buffer filtered by their aliases. SpookyStuff's execution engine is smart enough not to throw an error unless there is a conselectorflict - In which case you need to use different names for data and documents.

The following 2 variables are also treated as symbols with special meaning:

- S: Returns the only document in the unstructured document buffer, error out if multiple documents exist.

- S_*: Returns all documents in the unstructured document buffer.

#### string interpolation

You have seen the [basic form] of string interpolation early in some examples, which inserts only key-value pair(s) to a string template. The [above example] demonstrates a more powerful string interpolation, which inserts all kinds of expressions:

    x"<segment> ${<expression>} <segment> ${<expression>} <segment> ..."

Any non-expression, non-string typed identifier is treated as literal.

#### unstructured document traversing

These are operators that does the most important things: traversing DOM of unstructured documents to get data you want (potentially structured or semi-structured).

SpookyStuff supports a wide range of documents including HTML, XHTML, XML, JSON, and [all other formats supported by Apache Tika] (PDF, Microsoft Office etc.). In fact, most of the following operators are **format-agnostic**: they use different parsers on different formats to get the same DOM tree. Not all these formats have equivalent DOM representation (e.g. JSON doesn't have annotated text), so using a few operators on some formats doesn't make sense and always returns null value.

The exact format of a document is dictated by its mime-type indicated by the web resource, if it is not available, SpookyStuff will auto-detect it by analysing its extension name and binary content.

All operators that fits into this category are listed in the following table, with their compatible formats and explanation.

| Operator | Formats | Means |
| _.findAll("<Selector>") | All | returns all elements that qualify the selector provided, which should be exact field name for JSON type and jQuery selector for all others |
| _ \\ "<Selector>" | All | Same as above |
| _.findFirst("<Selector>") | All | first element returned by **findAll** |
| _.children("<Selector>") | All | returns only the direct children that qualify by the selector provided, which should be exact field name for JSON type and jQuery selector for all others |
| _ \ "<Selector>" | All | Same as above |
| _.child("<Selector>") | All | first element returned by **children** |
| S"<Selector>" | All | Big S selector: equivalent to S.findAll("<Selector>") |
| A"<Selector>" | All | Big A selector: equivalent to 'A.findAll("<Selector>"), 'A is the default symbol for the elements/data pivoted in flatten, flatSelect, join or explore |
| S_*"<Selector>" | All | Equivalent to S_*.findAll("<Selector>") |
| _.uri | All | URI of the document or DOM element, this may be different from the URI specified in Wget or Visit due to redirection(s) |
| _.code | All | returns the original HTML/XML/JSON code of the parsed document/DOM element as string |
| _.text | All | returns raw text of the parsed document/DOM element stripped of markups, on JSON this strips all field names and retains only their values |
| _.ownText | All | returns raw text of the parsed document/DOM element excluding those of its children, on JSON this returns null if the element is an object |
| _.attr("<attribute name>", [<nullable?>]) | All | returns an attribure value of the parsed document/DOM element, on JSON this returns a property preceded by "@", parameter <nullable?> decides whether a non-existing attribute should be returned as null or an empty string |
| _.href | All | same as .attr("href") on HTML/XML, same as .ownText on JSON |
| _.src | All | same as .attr("src") on HTML/XML, same as .ownText on JSON |
| _.boilerpipe | Non-JSON | use [boilerpipe](https://code.google.com/p/boilerpipe/) to extract the main textual content of a HTML file, this won't work for JSON |

#### others

Many other functions are also supported by the expression system but they are too many to be listed here. Scala users are recommended to refer to source code and scaladoc of ```org.tribbloid.spookystuff.dsl``` package, as these functions are built to resemble Scala functions and operators, rather than the less capable SQL LINQ syntax. In fact, SpookyStuff even use Scala reflective programming API to handle functions it doesn't know.

# More



# Profiling

SpookyStuff has a metric system based on Spark's [Accumulator](https://spark.apache.org/docs/latest/programming-guide.html#AccumLink), it can be accessed from **metrics** property under the SpookyContext:

    println(rows.spooky.metrics.toJSON)
    ...

By default each query keep track of its own metric, if you would like to have all metrics of queries from the same SpookyContext to be aggregated, simply set **conf.shareMetrics** under SpookyContext to *true*.

# Examples
