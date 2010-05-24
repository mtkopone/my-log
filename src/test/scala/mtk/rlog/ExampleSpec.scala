package mtk.rlog

import org.scalatest.Spec
import org.scalatest.matchers.{MustMatchers, ShouldMatchers}

class ExampleSpec extends Spec with MustMatchers {
  describe("a spec") {
    it("should do something") {
      1 must equal(1)
    }
  }
}