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
import static androidx.test.espresso.matcher.ViewMatchers.isEnabled;
import static androidx.test.espresso.matcher.ViewMatchers.isNotEnabled;

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

    @Test
    public void testAppBarDisplaysCorrectly() {
        onView(withId(R.id.appbar))
                .check(matches(isDisplayed()));
    }

    @Test
    public void testPostInputVisible() {
        onView(withId(R.id.post_input))
                .check(matches(isDisplayed()));
    }

    @Test
    public void testTypePostContent() {
        String testContent = "This is my new post!";

        onView(withId(R.id.post_input))
                .perform(typeText(testContent))
                .check(matches(withText(testContent)));
    }

    @Test
    public void testPostButtonDisabledWhenEmpty() {
        onView(withId(R.id.post_button))
                .check(matches(isNotEnabled()));
    }

    @Test
    public void testPostButtonEnabledWithText() {
        onView(withId(R.id.post_input))
                .perform(typeText("My new post"));

        onView(withId(R.id.post_button))
                .check(matches(isEnabled()));
    }

    @Test
    public void testUploadButtonsVisible() {
        onView(withId(R.id.upload_image_button))
                .check(matches(isDisplayed()));

        onView(withId(R.id.upload_video_button))
                .check(matches(isDisplayed()));
    }

    @Test
    public void testLocationAndTagRowsVisible() {
        onView(withId(R.id.add_location_row))
                .check(matches(isDisplayed()));

        onView(withId(R.id.tag_people_row))
                .check(matches(isDisplayed()));
    }

    @Test
    public void testPrivacySpinnerVisible() {
        onView(withId(R.id.privacy_spinner))
                .check(matches(isDisplayed()));
    }

    @Test
    public void testCameraButtonVisible() {
        onView(withId(R.id.camera_button))
                .check(matches(isDisplayed()));
    }

    @Test
    public void testCancelButtonClickable() {
        onView(withId(R.id.cancel_button))
                .check(matches(isDisplayed()))
                .perform(click());
    }

    @Test
    public void testMediaPreviewContainerPresent() {
        onView(withId(R.id.media_preview_container))
                .check(matches(isDisplayed()));
    }

    @Test
    public void testUserAvatarDisplayed() {
        onView(withId(R.id.user_avatar))
                .check(matches(isDisplayed()));
    }
}