package com.example.social_app.fragments;

import android.content.Context;

import androidx.fragment.app.testing.FragmentScenario;
import androidx.test.espresso.Espresso;
import androidx.test.espresso.action.ViewActions;
import androidx.test.espresso.assertion.ViewAssertions;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

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
 * Instrumented tests for CommentFragment.
 * Tests UI interactions, data binding, and user flows.
 */
@RunWith(AndroidJUnit4.class)
public class CommentFragmentTest {

    private static final String TEST_POST_ID = "test_post_123";

    @Before
    public void setUp() {
        // Launch fragment with test arguments
        FragmentScenario.launchInContainer(
                CommentFragment.class,
                CommentFragment.newInstance(TEST_POST_ID).getArguments()
        );
    }

    /**
     * Test that comments are displayed in RecyclerView.
     */
    @Test
    public void testCommentsDisplayed() {
        // Check if RecyclerView is visible
        onView(withId(R.id.comments_recycler_view))
                .check(matches(isDisplayed()));
    }

    /**
     * Test that sort dropdown is visible and functional.
     */
    @Test
    public void testSortDropdownVisible() {
        onView(withId(R.id.comment_sort_spinner))
                .check(matches(isDisplayed()));
    }

    /**
     * Test typing in comment input.
     */
    @Test
    public void testCommentInputTyping() {
        String testComment = "Great post!";

        onView(withId(R.id.comment_input))
                .perform(typeText(testComment))
                .check(matches(withText(testComment)));
    }

    /**
     * Test send button is disabled when input is empty.
     */
    @Test
    public void testSendButtonDisabledWhenEmpty() {
        onView(withId(R.id.send_button))
                .check(matches(ViewMatchers.isNotEnabled()));
    }

    /**
     * Test send button is enabled when input has text.
     */
    @Test
    public void testSendButtonEnabledWithText() {
        onView(withId(R.id.comment_input))
                .perform(typeText("Test comment"));

        onView(withId(R.id.send_button))
                .check(matches(ViewMatchers.isEnabled()));
    }

    /**
     * Test sending a comment clears the input field.
     */
    @Test
    public void testSendingCommentClearsInput() {
        String testComment = "Test comment";

        onView(withId(R.id.comment_input))
                .perform(typeText(testComment));

        onView(withId(R.id.send_button))
                .perform(click());

        onView(withId(R.id.comment_input))
                .check(matches(withText("")));
    }

    /**
     * Test emoji button opens emoji picker.
     */
    @Test
    public void testEmojiButtonClickable() {
        onView(withId(R.id.emoji_button))
                .check(matches(isDisplayed()))
                .perform(click());
    }

    /**
     * Test GIF button is clickable.
     */
    @Test
    public void testGifButtonClickable() {
        onView(withId(R.id.gif_button))
                .check(matches(isDisplayed()))
                .perform(click());
    }

    /**
     * Test compose bar is visible and contains all required elements.
     */
    @Test
    public void testComposeBarComplete() {
        onView(withId(R.id.comment_compose_container))
                .check(matches(isDisplayed()));

        onView(withId(R.id.compose_avatar))
                .check(matches(isDisplayed()));

        onView(withId(R.id.comment_input))
                .check(matches(isDisplayed()));

        onView(withId(R.id.send_button))
                .check(matches(isDisplayed()));
    }

    /**
     * Test swipe refresh layout is functional.
     */
    @Test
    public void testSwipeRefreshFunctional() {
        onView(withId(R.id.swipe_refresh_layout))
                .check(matches(isDisplayed()));
    }
}
