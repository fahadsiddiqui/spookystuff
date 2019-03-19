package com.tribbloids.spookystuff.graph

import com.tribbloids.spookystuff.graph.example.SimpleGraph.Face
import com.tribbloids.spookystuff.testutils.FunSpecx

class SimpleGraphSuite extends FunSpecx {

  it("create DSL interface from NodeData") {

    val face = Face.fromNodeData(Some("ABC"))
    val str = face.visualise().show()
    str shouldBe """
                   |>>- -->
                   |v (TAIL>>-) [ None ]
                   |+- Some(ABC)
                   |   +- v (HEAD) [ None ]
                   |<-- -<<
                   |v (TAIL-<<) [ None ]
                   |+- Some(ABC)
                   |   +- v (HEAD) [ None ]
                 """.stripMargin
  }

  it("... implicitly") {

    val face: Face = Some("ABC")
    val str = face.visualise().show()
    str shouldBe """
                   |>>- -->
                   |v (TAIL>>-) [ None ]
                   |+- Some(ABC)
                   |   +- v (HEAD) [ None ]
                   |<-- -<<
                   |v (TAIL-<<) [ None ]
                   |+- Some(ABC)
                   |   +- v (HEAD) [ None ]
                 """.stripMargin

  }

  it("create DSL interface from EdgeData") {

    val face = Face.fromEdgeData(Some("ABC"))
    val str = face.visualise().show()
    str shouldBe """
                   |>>- -->
                   |v (HEAD)(TAIL>>- -<<) [ Some(ABC) ]
                   |<-- -<<
                   |v (HEAD)(TAIL>>- -<<) [ Some(ABC) ]
                 """.stripMargin

  }

  it("merge 2 nodes") {

    val f1: Face = Some("ABC")
    val f2: Face = Some("DEF")

    val linked = f1 >>> f2
    linked.visualise().show() shouldBe
      """
        |>>- -->
        |v (TAIL>>-) [ None ]
        |+- Some(ABC)
        |   +- v  [ None ]
        |      +- Some(DEF)
        |         +- v (HEAD) [ None ]
        |<-- -<<
        |v (TAIL-<<) [ None ]
        |+- Some(DEF)
        |   +- v (HEAD) [ None ]
      """.stripMargin

    linked
      .visualise()
      .show(asciiArt = true)
      .shouldBe(
        """
        |      ┌──────────────────┐
        |      │(TAIL>>-) [ None ]│
        |      └─────────┬────────┘
        |                │
        |                v
        |           ┌─────────┐
        |           │Some(ABC)│
        |           └────┬────┘
        |                │
        |      ┌─────────┘
        |      │
        |      v
        | ┌─────────┐ ┌──────────────────┐
        | │ [ None ]│ │(TAIL-<<) [ None ]│
        | └────┬────┘ └────┬─────────────┘
        |      │           │
        |      └────────┐  │
        |               │  │
        |               v  v
        |           ┌─────────┐
        |           │Some(DEF)│
        |           └────┬────┘
        |                │
        |                v
        |        ┌───────────────┐
        |        │(HEAD) [ None ]│
        |        └───────────────┘
      """.stripMargin
      )
  }

  it("merge 2 detached edges") {

    val f1: Face = Face.fromEdgeData(Some("ABC"))
    val f2: Face = Face.fromEdgeData(Some("DEF"))

    val linked = f1 >>> f2
    linked.visualise().show() shouldBe """
                                         |>>- -->
                                         |v (HEAD)(TAIL>>- -<<) [ Some(ABCDEF) ]
                                         |<-- -<<
                                         |v (HEAD)(TAIL>>- -<<) [ Some(ABCDEF) ]
                                       """.stripMargin
  }

  it("1 node merge with itself, forming a loop") {

    val node: Face = Some("ABC")
    val edge: Face = Face.fromEdgeData(Some("loop"))

    val n1 = node >>> edge
    n1.visualise().show() shouldBe """
                                     |>>- -->
                                     |v (TAIL>>-) [ None ]
                                     |+- Some(ABC)
                                     |   +- v (HEAD) [ Some(loop) ]
                                     |<-- -<<
                                   """.stripMargin

    val loop = node >>> edge >>> node
    loop.visualise().show() shouldBe ()
  }

  //  it("1 node merge with itself")
}