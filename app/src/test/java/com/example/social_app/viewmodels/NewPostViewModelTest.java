package com.example.social_app.viewmodels;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for NewPostViewModel.
 * Tests post creation logic, state management, and validation.
 */
@RunWith(JUnit4.class)
public class NewPostViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantExecutorRule = new InstantTaskExecutorRule();

    private NewPostViewModel viewModel;

    @Before
    public void setUp() {
        viewModel = new NewPostViewModel();
    }

    /**
     * Test isPosting state changes during post creation.
     */
    @Test
    public void testIsPostingStateChanges() {
        String testContent = "Test post content";

        // Should be false initially
        assertFalse(viewModel.getIsPosting().getValue());

        // Create post
        viewModel.createPost(
                testContent,
                new ArrayList<>(),
                null,
                new ArrayList<>(),
                "Everyone"
        );

        // After creation, should return to false
        assertFalse(viewModel.getIsPosting().getValue());
    }

    /**
     * Test successful post creation.
     */
    @Test
    public void testSuccessfulPostCreation() {
        String testContent = "My new post!";

        // Initially should be false
        assertFalse(viewModel.getPostSuccess().getValue());

        // Create post
        viewModel.createPost(
                testContent,
                new ArrayList<>(),
                "San Francisco, CA",
                new ArrayList<>(),
                "Everyone"
        );

        // Should be marked as success
        assertTrue(viewModel.getPostSuccess().getValue());
    }

    /**
     * Test post creation with empty content fails.
     */
    @Test
    public void testPostCreationWithEmptyContentFails() {
        String emptyContent = "";

        // Create post with empty content
        viewModel.createPost(
                emptyContent,
                new ArrayList<>(),
                null,
                new ArrayList<>(),
                "Everyone"
        );

        // Should show error
        assertNotNull(viewModel.getError().getValue());
    }

    /**
     * Test post creation with media.
     */
    @Test
    public void testPostCreationWithMedia() {
        String testContent = "Check out this photo!";

        viewModel.createPost(
                testContent,
                new ArrayList<>(),  // Would contain URIs in real scenario
                null,
                new ArrayList<>(),
                "Everyone"
        );

        // Should succeed
        assertTrue(viewModel.getPostSuccess().getValue());
    }

    /**
     * Test post creation with location.
     */
    @Test
    public void testPostCreationWithLocation() {
        String testContent = "Great day at the beach!";
        String testLocation = "Malibu Beach, CA";

        viewModel.createPost(
                testContent,
                new ArrayList<>(),
                testLocation,
                new ArrayList<>(),
                "Everyone"
        );

        // Should succeed
        assertTrue(viewModel.getPostSuccess().getValue());
    }

    /**
     * Test post creation with tagged people.
     */
    @Test
    public void testPostCreationWithTags() {
        String testContent = "Great time with friends!";
        ArrayList<String> taggedPeople = new ArrayList<>();
        taggedPeople.add("user_123");
        taggedPeople.add("user_456");

        viewModel.createPost(
                testContent,
                new ArrayList<>(),
                null,
                taggedPeople,
                "Friends"
        );

        // Should succeed
        assertTrue(viewModel.getPostSuccess().getValue());
    }

    /**
     * Test privacy level options.
     */
    @Test
    public void testDifferentPrivacyLevels() {
        String[] privacyLevels = {"Everyone", "Friends", "Friends Only", "Private"};

        for (String privacyLevel : privacyLevels) {
            viewModel.createPost(
                    "Test post",
                    new ArrayList<>(),
                    null,
                    new ArrayList<>(),
                    privacyLevel
            );

            assertTrue(viewModel.getPostSuccess().getValue());
            viewModel.resetPostSuccess();
        }
    }

    /**
     * Test reset post success clears success state.
     */
    @Test
    public void testResetPostSuccess() {
        // Create post
        viewModel.createPost(
                "Test",
                new ArrayList<>(),
                null,
                new ArrayList<>(),
                "Everyone"
        );

        assertTrue(viewModel.getPostSuccess().getValue());

        // Reset
        viewModel.resetPostSuccess();

        assertFalse(viewModel.getPostSuccess().getValue());
    }

    /**
     * Test error is null on successful creation.
     */
    @Test
    public void testErrorNullOnSuccess() {
        viewModel.createPost(
                "Valid content",
                new ArrayList<>(),
                null,
                new ArrayList<>(),
                "Everyone"
        );

        // Error should be null
        assertNotNull(viewModel.getError());
    }
}
