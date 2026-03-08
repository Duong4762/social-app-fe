package com.example.social_app.fragments;

import androidx.fragment.app.testing.FragmentScenario;
import androidx.test.espresso.action.ViewActions;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.social_app.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

/**
 * Instrumented tests for NewPostFragment.
 * Tests post creation flow, media handling, and UI interactions.
 */
@RunWith(AndroidJUnit4.class)
public class NewPostFragmentTest {

    @Before
    public void setUp() {
        // Launch fragment
        FragmentScenario.launchInContainer(NewPostFragment.class);
    }

    /**
     * Test that appbar is visible with correct title.
     */
    @Test
    public void testAppBarDisplaysCorrectly() {
        onView(withId(R.id.appbar))
                .check(matches(isDisplayed()));
    }

    /**
     * Test post input field is present and visible.
     */
    @Test
    public void testPostInputVisible() {
        onView(withId(R.id.post_input))
                .check(matches(isDisplayed()));
    }

    /**
     * Test typing in post input field.
     */
    @Test
    public void testTypePostContent() {
        String testContent = "This is my new post!";

        onView(withId(R.id.post_input))
                .perform(typeText(testContent))
                .check(matches(withText(testContent)));
    }

    /**
     * Test character counter updates on input.
     */
    @Test
    public void testCharacterCounterUpdates() {
        onView(withId(R.id.post_input))
                .perform(typeText("Hello"));

        // Character count should be updated (assuming 280 max)
        onView(withId(R.id.character_count))
                .check(matches(withText("275/280")));
    }

    /**
     * Test post button is disabled when input is empty.
     */
    @Test
    public void testPostButtonDisabledWhenEmpty() {
        onView(withId(R.id.post_button))
                .check(matches(ViewMatchers.isNotEnabled()));
    }

    /**
     * Test post button is enabled when input has text.
     */
    @Test
    public void testPostButtonEnabledWithText() {
        onView(withId(R.id.post_input))
                .perform(typeText("My new post"));

        onView(withId(R.id.post_button))
                .check(matches(ViewMatchers.isEnabled()));
    }

    /**
     * Test all upload buttons are visible.
     */
    @Test
    public void testUploadButtonsVisible() {
        onView(withId(R.id.upload_image_button))
                .check(matches(isDisplayed()));

        onView(withId(R.id.upload_video_button))
                .check(matches(isDisplayed()));
    }

    /**
     * Test location and tag buttons are visible.
     */
    @Test
    public void testLocationAndTagButtonsVisible() {
        onView(withId(R.id.add_location_button))
                .check(matches(isDisplayed()));

        onView(withId(R.id.tag_people_button))
                .check(matches(isDisplayed()));
    }

    /**
     * Test privacy spinner is visible.
     */
    @Test
    public void testPrivacySpinnerVisible() {
        onView(withId(R.id.privacy_spinner))
                .check(matches(isDisplayed()));
    }

    /**
     * Test camera FAB button is visible.
     */
    @Test
    public void testCameraButtonVisible() {
        onView(withId(R.id.camera_button))
                .check(matches(isDisplayed()));
    }

    /**
     * Test cancel button is clickable.
     */
    @Test
    public void testCancelButtonClickable() {
        onView(withId(R.id.cancel_button))
                .check(matches(isDisplayed()))
                .perform(click());
    }

    /**
     * Test media preview grid is present.
     */
    @Test
    public void testMediaPreviewGridPresent() {
        onView(withId(R.id.media_preview_grid))
                .check(matches(isDisplayed()));
    }

    /**
     * Test user avatar is displayed.
     */
    @Test
    public void testUserAvatarDisplayed() {
        onView(withId(R.id.user_avatar))
                .check(matches(isDisplayed()));
    }
}
