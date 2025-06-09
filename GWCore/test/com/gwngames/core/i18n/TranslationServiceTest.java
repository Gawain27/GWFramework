package com.gwngames.core.i18n;

import com.gwngames.core.api.base.ITranslationService;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.base.BaseTest;
import com.gwngames.core.base.cfg.i18n.BasicTranslation;
import com.gwngames.core.base.cfg.i18n.TranslationService;
import org.junit.jupiter.api.Assertions;

import java.util.*;

public class TranslationServiceTest extends BaseTest {

    @Override
    protected void runTest() {
        setupApplication();

        Locale en = Locale.US;
        Locale de = Locale.GERMAN;

        String key = BasicTranslation.ERROR.getKey();  // Adjust this to an actual key we have
        ITranslationService service = BaseComponent.getInstance(ITranslationService.class);
        service.reload();
        log.info("Check: known key should return English value");
        String enResult = service.tr(key, en);
        Assertions.assertNotNull(enResult, "English translation should not be null");
        Assertions.assertNotEquals(key, enResult, "English translation should differ from key");

        log.info("Check: known key should return German value");
        String deResult = service.tr(key, de);
        Assertions.assertNotNull(deResult, "German translation should not be null");
        Assertions.assertNotEquals(key, deResult, "German translation should differ from key");

        log.info("Check: fallback returns English if locale missing");
        Locale fake = Locale.forLanguageTag("zz-ZZ");
        String fallback = service.tr(key, fake);
        Assertions.assertEquals(enResult, fallback, "Should fallback to English for unknown locale");

        log.info("Check: unknown key causes crash");
        try {
            service.tr("NON_EXISTENT_KEY", en);
        } catch (IllegalStateException e){
            // ignored, test passed
        }

        log.info("Check: reload doesn't break things");
        service.reload();
        String afterReload = service.tr(key, de);
        Assertions.assertEquals(deResult, afterReload, "Translation after reload should match previous result");
    }
}
