package mtk.rlog.tests 

import junit.framework.Assert._
import _root_.android.test.AndroidTestCase

class UnitTests extends AndroidTestCase {
  def testPackageIsCorrect {      
    assertEquals("mtk.rlog", getContext.getPackageName)
  }
}