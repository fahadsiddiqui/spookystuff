package com.tribbloids.spookystuff.parsing

import com.tribbloids.spookystuff.parsing.FSMParserDSL._
import com.tribbloids.spookystuff.testutils.FunSpecx

class FSMParserDSLSuite extends FunSpecx {

  it("can form linear graph") {

    val p = P_*('$') :~> P('{') :~> P_*('}') :~> FINISH

//    val forward = p.visualise(Format(forward = true)).Tree.show
//    val backward = p.visualise(Format(forward = false)).Tree.show

    p.visualise()
      .ASCIIArt()
      .shouldBe(
        """
          | ╔═══════════════╗ ╔═══════════════╗
          | ║(TAIL>>-) [ ∅ ]║ ║(TAIL-<<) [ ∅ ]║
          | ╚══════════════╤╝ ╚═══════╤═══════╝
          |                │          │
          |                │ ┌────────┘
          |                │ │
          |                v v
          |              ╔═════╗
          |              ║ROOT ║
          |              ╚═══╤═╝
          |                  │
          |                  v
          |          ╔══════════════╗
          |          ║[ '$' [0...] ]║
          |          ╚══════╤═══════╝
          |                 │
          |                 v
          |               ╔═══╗
          |               ║---║
          |               ╚═╤═╝
          |                 │
          |                 v
          |           ╔═══════════╗
          |           ║[ '{' [0] ]║
          |           ╚═════╤═════╝
          |                 │
          |                 v
          |               ╔═══╗
          |               ║---║
          |               ╚══╤╝
          |                  │
          |                  v
          |          ╔══════════════╗
          |          ║[ '}' [0...] ]║
          |          ╚═══════╤══════╝
          |                  │
          |                  v
          |              ╔══════╗
          |              ║FINISH║
          |              ╚═══╤══╝
          |                  │
          |                  v
          |           ╔════════════╗
          |           ║(HEAD) [ ∅ ]║
          |           ╚════════════╝
        """.stripMargin
      )
  }

  it("can form non-linear graph") {

    val p = P_*('P') :~> (P('1') U P('2')) :~> FINISH
    p.visualise()
      .ASCIIArt()
      .shouldBe(
        """
          | ╔═══════════════╗ ╔═══════════════╗
          | ║(TAIL>>-) [ ∅ ]║ ║(TAIL-<<) [ ∅ ]║
          | ╚══════════════╤╝ ╚═══════╤═══════╝
          |                │          │
          |                │ ┌────────┘
          |                │ │
          |                v v
          |              ╔═════╗
          |              ║ROOT ║
          |              ╚═══╤═╝
          |                  │
          |                  v
          |          ╔══════════════╗
          |          ║[ 'P' [0...] ]║
          |          ╚══════╤═══════╝
          |                 │
          |                 v
          |              ╔═════╗
          |              ║ --- ║
          |              ╚═╤═╤═╝
          |                │ │
          |         ┌──────┘ └──────┐
          |         │               │
          |         v               v
          |   ╔═══════════╗   ╔═══════════╗
          |   ║[ '1' [0] ]║   ║[ '2' [0] ]║
          |   ╚═════╤═════╝   ╚═════╤═════╝
          |         │               │
          |         └───────┐ ┌─────┘
          |                 │ │
          |                 v v
          |              ╔══════╗
          |              ║FINISH║
          |              ╚═══╤══╝
          |                  │
          |                  v
          |           ╔════════════╗
          |           ║(HEAD) [ ∅ ]║
          |           ╚════════════╝
        """.stripMargin
      )
  }

  it("can form loop") {

    val start = P_*('{')
    val p = start :~> P_*('}') :& start :~> EOS_* :~> FINISH
    p.visualise()
      .ASCIIArt()
      .shouldBe(
        """
          | ╔═══════════════╗ ╔═══════════════╗
          | ║(TAIL>>-) [ ∅ ]║ ║(TAIL-<<) [ ∅ ]║
          | ╚═══════╤═══════╝ ╚═╤═════════════╝
          |         │           │
          |         └────────┐  │
          |                  │  │
          |                  v  v
          |               ╔═══════╗
          |               ║ ROOT  ║
          |               ╚═╤═╤═══╝
          |                 │ │ ^
          |         ┌───────┘ │ └─────────────────┐
          |         │         │                   │
          |         v         v                   │
          | ╔══════════════╗ ╔══════════════════╗ │
          | ║[ '{' [0...] ]║ ║[ '[EOS]' [0...] ]║ │
          | ╚══════╤═══════╝ ╚══╤═══════════════╝ │
          |        │    ┌───────┼─────────────────┘
          |        v    │       v
          |      ╔═══╗  │   ╔══════╗
          |      ║---║  │   ║FINISH║
          |      ╚═╤═╝  │   ╚═══╤══╝
          |        │    │       │
          |        │    │       └───────┐
          |        │    │               │
          |        v    │               v
          |   ╔═════════╧════╗   ╔════════════╗
          |   ║[ '}' [0...] ]║   ║(HEAD) [ ∅ ]║
          |   ╚══════════════╝   ╚════════════╝
        """.stripMargin
      )
  }

  it("can form self-loop") {

    val escape = P_*('\\')
    val p = escape :& escape :~> P_*('$') :~> FINISH
    p.visualise()
      .ASCIIArt()
      .shouldBe(
        """
          | ╔═══════════════╗ ╔═══════════════╗
          | ║(TAIL>>-) [ ∅ ]║ ║(TAIL-<<) [ ∅ ]║
          | ╚══════════════╤╝ ╚═══════╤═══════╝
          |                │          │
          |                │  ┌───────┘
          |                │  │
          |                v  v
          |             ╔═══════╗
          |             ║ ROOT  ║
          |             ╚═╤═══╤═╝
          |               │ ^ │
          |               │ │ └───────┐
          |               │ └──┐     ┌┘
          |               │    └┐    │
          |               v     │    │
          |    ╔══════════════╗ │    │
          |    ║[ '$' [0...] ]║ │    │
          |    ╚═════╤════════╝ │    │
          |          │          │    │
          |          v          │    │
          |      ╔══════╗       │    │
          |      ║FINISH║       │    │
          |      ╚═╤════╝       │    │
          |        │            │    │
          |        v            │    v
          | ╔════════════╗ ╔════╧═════════╗
          | ║(HEAD) [ ∅ ]║ ║[ '\' [0...] ]║
          | ╚════════════╝ ╚══════════════╝
        """.stripMargin
      )
  }

  it("self-loop can union with others") {
    val escape = P_*('\\')

    val _p = escape :& escape :~> P_*('$') :~> EOS_* :~> FINISH
    val p = _p U (EOS_* :~> FINISH)

    p.visualise()
      .ASCIIArt()
      .shouldBe(
        """
          |   ╔═══════════════╗   ╔═══════════════╗
          |   ║(TAIL>>-) [ ∅ ]║   ║(TAIL-<<) [ ∅ ]║
          |   ╚═══════╤═══════╝   ╚╤══════════════╝
          |           │            │
          |           └─────────┐  │
          |                     │  │
          |                     v  v
          |                 ╔═════════╗
          |                 ║  ROOT   ║
          |                 ╚═╤════╤╤═╝
          |                   │   ^││
          |            ┌──────┘   ││└─────────┐
          |            │          │└───────┐  │
          |            │          └───┐    │  │
          |            v              │    │  │
          |    ╔══════════════╗       │    │  │
          |    ║[ '$' [0...] ]║       │    │  │
          |    ╚════╤═════════╝       │    │  │
          |         │                 │    │  │
          |         v                 │    │  │
          |       ╔═══╗               │    │  │
          |       ║---║               │    │  │
          |       ╚═╤═╝               │    │  │
          |         │                 │    │  │
          |         │                 │    │  └─────────┐
          |         │                 └────┼──────────┐ │
          |         v                      v          │ │
          | ╔══════════════════╗ ╔══════════════════╗ │ │
          | ║[ '[EOS]' [0...] ]║ ║[ '[EOS]' [0...] ]║ │ │
          | ╚═════════╤════════╝ ╚═════════╤════════╝ │ │
          |           │                    │          │ │
          |           │ ┌──────────────────┘          │ │
          |           │ │               ┌─────────────┘ │
          |           v v               │               │
          |        ╔══════╗             │               │
          |        ║FINISH║             │               │
          |        ╚═══╤══╝             │               │
          |            │                │               │
          |            │                │    ┌──────────┘
          |            │                │    │
          |            v                │    v
          |     ╔════════════╗     ╔════╧═════════╗
          |     ║(HEAD) [ ∅ ]║     ║[ '\' [0...] ]║
          |     ╚════════════╝     ╚══════════════╝
        """.stripMargin
      )
  }
}
