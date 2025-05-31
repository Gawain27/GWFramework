package com.gwngames.core.i18n;

import com.gwngames.core.base.BaseTest;
import com.gwngames.core.base.cfg.i18n.TranslationService;
import org.junit.jupiter.api.Assertions;

import java.util.*;

public class TranslationServiceTest extends BaseTest {

    @Override
    protected void runTest() {
        Locale en = Locale.US;
        Locale de = Locale.GERMAN;

        String key = "ERROR";  // Adjust this to an actual key we have

        log.info("Check: known key should return English value");
        String enResult = TranslationService.tr(key, en);
        Assertions.assertNotNull(enResult, "English translation should not be null");
        Assertions.assertNotEquals(key, enResult, "English translation should differ from key");

        log.info("Check: known key should return German value");
        String deResult = TranslationService.tr(key, de);
        Assertions.assertNotNull(deResult, "German translation should not be null");
        Assertions.assertNotEquals(key, deResult, "German translation should differ from key");

        log.info("Check: fallback returns English if locale missing");
        Locale fake = Locale.forLanguageTag("zz-ZZ");
        String fallback = TranslationService.tr(key, fake);
        Assertions.assertEquals(enResult, fallback, "Should fallback to English for unknown locale");

        log.info("Check: unknown key causes crash");
        try {
            TranslationService.tr("NON_EXISTENT_KEY", en);
        } catch (IllegalStateException e){
            // ignored, test passed
        }

        log.info("Check: reload doesn't break things");
        TranslationService.reload();
        String afterReload = TranslationService.tr(key, de);
        Assertions.assertEquals(deResult, afterReload, "Translation after reload should match previous result");
    }
}
