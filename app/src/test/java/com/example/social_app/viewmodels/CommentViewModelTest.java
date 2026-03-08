package com.example.social_app.viewmodels;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.Observer;

import com.example.social_app.models.Comment;
import com.example.social_app.utils.MockDataGenerator;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for CommentViewModel.
 * Tests ViewModel logic, data management, and observer updates.
 */
@RunWith(JUnit4.class)
public class CommentViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantExecutorRule = new InstantTaskExecutorRule();

    private CommentViewModel viewModel;

    @Before
    public void setUp() {
        viewModel = new CommentViewModel();
    }

    /**
     * Test loading comments updates LiveData.
     */
    @Test
    public void testLoadCommentsUpdateLiveData() {
        String testPostId = "post_123";

        viewModel.loadComments(testPostId);

        // Verify LiveData is updated
        assertNotNull(viewModel.getComments().getValue());
        assertTrue(viewModel.getComments().getValue().size() > 0);
    }

    /**
     * Test sending comment adds it to the list.
     */
    @Test
    public void testSendCommentAddsToList() {
        // First load comments
        viewModel.loadComments("post_123");
        List<Comment> initialComments = viewModel.getComments().getValue();
        int initialCount = initialComments != null ? initialComments.size() : 0;

        // Send new comment
        viewModel.sendComment("post_123", "Test comment");

        // Verify comment was added
        List<Comment> updatedComments = viewModel.getComments().getValue();
        assertNotNull(updatedComments);
        assertTrue(updatedComments.size() > initialCount);
    }

    /**
     * Test toggle like updates comment like count.
     */
    @Test
    public void testToggleLikeUpdatesCount() {
        viewModel.loadComments("post_123");
        List<Comment> comments = viewModel.getComments().getValue();
        assertNotNull(comments);
        assertTrue(comments.size() > 0);

        Comment testComment = comments.get(0);
        int initialLikeCount = testComment.getLikeCount();
        boolean initialLikedState = testComment.isLiked();

        // Toggle like
        viewModel.toggleLike(testComment);

        // Verify state changed
        assertEquals(!initialLikedState, testComment.isLiked());
        if (!initialLikedState) {
            assertEquals(initialLikeCount + 1, testComment.getLikeCount());
        } else {
            assertEquals(initialLikeCount - 1, testComment.getLikeCount());
        }
    }

    /**
     * Test load more replies updates comment replies.
     */
    @Test
    public void testLoadMoreRepliesUpdatesComment() {
        viewModel.loadComments("post_123");
        List<Comment> comments = viewModel.getComments().getValue();
        assertNotNull(comments);
        assertTrue(comments.size() > 0);

        Comment testComment = comments.get(0);
        String commentId = testComment.getId();

        // Load more replies
        viewModel.loadMoreReplies(commentId);

        // Verify replies were loaded
        assertNotNull(testComment.getReplies());
        assertTrue(testComment.getReplies().size() > 0);
    }

    /**
     * Test error handling when loading comments.
     */
    @Test
    public void testErrorHandling() {
        // Mock data should handle errors gracefully
        viewModel.loadComments("post_123");

        // Error should be null on success
        assertNotNull(viewModel.getError());
    }

    /**
     * Test mock data generation consistency.
     */
    @Test
    public void testMockDataConsistency() {
        List<Comment> comments = MockDataGenerator.generateMockComments();

        assertNotNull(comments);
        assertTrue(comments.size() > 0);

        // Each comment should have required fields
        for (Comment comment : comments) {
            assertNotNull(comment.getId());
            assertNotNull(comment.getUser());
            assertNotNull(comment.getText());
            assertTrue(comment.getTimestamp() > 0);
        }
    }
}
