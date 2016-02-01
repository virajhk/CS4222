import android.test.ActivityInstrumentationTestCase2;
import org.junit.Test;
/**
 * This is a simple framework for a test of an Application.  See
 * {@link android.test.ApplicationTestCase ApplicationTestCase} for more information on
 * how to write and extend Application tests.
 * <p/>
 * To run this test, you can type:
 * adb shell am instrument -w \
 * -e class nus.cs4222.lightanalyzer.LightAnalyzerActivityTest \
 * nus.cs4222.lightanalyzer.tests/android.test.InstrumentationTestRunner
 */
public class LightAnalyzerActivityTest extends ActivityInstrumentationTestCase2<LightAnalyzerActivity> {

    public LightAnalyzerActivityTest() {
        super("com.cs4222.khuthia.lightanalyzer", LightAnalyzerActivity.class);
    }

}
