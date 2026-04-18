package com.example.social_app.utils;

import com.example.social_app.models.Comment;
import com.example.social_app.models.Post;
import com.example.social_app.models.User;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for generating mock data for the social feed.
 * This is used for demonstration and testing purposes.
 */
public class MockDataGenerator {

    private static final String[] FIRST_NAMES = {
            "Alex", "Jordan", "Casey", "Morgan", "Riley", "Taylor",
            "Avery", "Quinn", "Jamie", "Sam", "Chris", "Blake",
            "Dakota", "Madison", "Brooklyn", "Austin"
    };

    private static final String[] LAST_NAMES = {
            "Chen", "Anderson", "Kumar", "Silva", "Rodriguez", "Chen",
            "Kim", "Johnson", "Williams", "Brown", "Martinez", "Garcia",
            "Lee", "Lopez", "Gonzalez", "Patel"
    };

    private static final String[] POST_CONTENTS = {
            "Just finished an amazing hike at the mountain! The view from the top was absolutely breathtaking. Nature never ceases to amaze me! 🏔️",
            "Coffee and code - the perfect morning combination! Working on some exciting new features for our app. #Development #Programming",
            "Spotted this beautiful sunset while walking by the beach. Nothing beats nature's painting skills! 🌅",
            "Just launched my new portfolio website! Check it out and let me know what you think. All feedback is welcome! 💻",
            "Finally tried that new restaurant everyone's been talking about. The food was incredible! Highly recommend if you're in the area. 🍽️",
            "Can't believe it's already Friday! Time for some well-deserved relaxation. What are your weekend plans? 🎉",
            "Working on improving my photography skills. Here's a shot I'm really proud of! Constructive criticism welcome. 📸",
            "Just completed my first marathon! Feeling accomplished and exhausted at the same time. Thanks to everyone who supported me! 🏃",
            "Weekend gaming session with friends. Nothing beats some quality time gaming together! #GamingCommunity",
            "Beautiful day for a picnic! Enjoying the outdoor vibes and good company. #NatureLovers",
            "Finally organized my home office. Productivity boost incoming! #WorkFromHome",
            "Just started learning a new programming language. Excited to see where this journey takes me! 🚀",
            "Coffee date with my best friend! Missing these moments. Let's do this more often! ☕",
            "Finished reading an amazing book today. Highly recommend it if you love sci-fi! 📚"
    };

    private static final String[] LOCATIONS = {
            "San Francisco, CA", "New York, NY", "Los Angeles, CA", "Chicago, IL",
            "Austin, TX", "Seattle, WA", "Boston, MA", "Denver, CO",
            "Miami, FL", "Portland, OR", "Kyoto, Japan", "Tokyo, Japan",
            "Paris, France", "London, UK", "Barcelona, Spain"
    };

    private static final String[] COMMENT_TEXTS = {
            "Great shot! Love the composition and colors! 📸",
            "This is exactly what I needed to see today. Thanks for sharing!",
            "Amazing work! How long did this take you?",
            "Couldn't agree more! This resonates with me.",
            "The detail is incredible! Well done! 🎉",
            "This made my day better. Thank you!",
            "Absolutely beautiful! Where was this taken?",
            "So inspiring! Keep up the amazing content!",
            "This is gold! Sharing this with everyone I know.",
            "Love the energy and vibes in this post!",
            "100% agree with everything you said here.",
            "This deserves way more likes and comments!",
            "Thank you for the inspiration and great insights!",
            "The way you captured this is just perfect!"
    };

    private static final int[] MOCK_IMAGE_RESOURCES = {
            // These are placeholder indices - in a real app, these would be actual drawable IDs
            1, 2, 3, 4, 5, 6, 7, 8, 9
    };

    /**
     * Generates a list of mock posts for demonstration.
     *
     * @param count Number of posts to generate
     * @return List of mock Post objects
     */
    public static List<Post> generateMockPosts(int count) {
        List<Post> posts = new ArrayList<>();
        long currentTime = System.currentTimeMillis();

        for (int i = 0; i < count; i++) {
            String postId = "post_" + i;
            User user = generateMockUser(i);

            String content = POST_CONTENTS[i % POST_CONTENTS.length];
            String location = i % 3 == 0 ? LOCATIONS[i % LOCATIONS.length] : null;

            // Create timestamps distributed over the past week
            long timestamp = currentTime - (i * Constants.HOUR_IN_MILLIS * 3);

            int likeCount = (i + 1) * 15 + (i % 10) * 3;
            int commentCount = (i + 1) * 5 + (i % 7) * 2;
            int shareCount = (i + 1) * 2 + (i % 5);

            Post post = new Post(
                    postId,
                    user,
                    content,
                    "drawable/post_image_" + (i % MOCK_IMAGE_RESOURCES.length + 1), // Mock image reference
                    location,
                    timestamp,
                    likeCount,
                    commentCount,
                    shareCount
            );

            posts.add(post);
        }

        return posts;
    }

    /**
     * Generates a mock user.
     *
     * @param index Index to generate varied users
     * @return Generated User object
     */
    public static User generateMockUser(int index) {
        String firstName = FIRST_NAMES[index % FIRST_NAMES.length];
        String lastName = LAST_NAMES[index % LAST_NAMES.length];
        String name = firstName + " " + lastName;
        String userId = "user_" + index;
        String bio = "Digital creator | " + (index % 2 == 0 ? "Tech Enthusiast" : "Nature Lover");

        return new User(
                userId,
                name,
                "drawable/avatar_placeholder", // Placeholder avatar
                bio
        );
    }

    /**
     * Generates a mock user without index parameter.
     *
     * @return Generated User object
     */
    public static User generateMockUser() {
        return generateMockUser((int) (Math.random() * FIRST_NAMES.length));
    }

    /**
     * Generates a list of mock comments for demonstration.
     *
     * @return List of mock Comment objects
     */
    public static List<Comment> generateMockComments() {
        List<Comment> comments = new ArrayList<>();
        long currentTime = System.currentTimeMillis();

        for (int i = 0; i < 8; i++) {
            String commentId = "comment_" + System.currentTimeMillis() + "_" + i;
            User user = generateMockUser(i);

            // Add verification badge to some users
            if (i % 3 == 0) {
                user.setVerified(true);
            }

            String text = COMMENT_TEXTS[i % COMMENT_TEXTS.length];
            long timestamp = currentTime - (i * Constants.HOUR_IN_MILLIS);
            int likeCount = (i + 1) * 5 + (i % 4) * 3;
            List<Comment> replies = i % 2 == 0 ? generateMockReplies() : null;
            boolean hasMoreReplies = i % 4 == 0;

            Comment comment = new Comment(
                    commentId,
                    user,
                    text,
                    timestamp,
                    likeCount,
                    replies,
                    hasMoreReplies
            );

            // Add location to some comments
            if (i % 2 == 0) {
                comment.setLocation(LOCATIONS[i % LOCATIONS.length]);
            }

            comments.add(comment);
        }

        return comments;
    }

    /**
     * Generates mock replies for a comment.
     *
     * @return List of mock Comment objects as replies
     */
    private static List<Comment> generateMockReplies() {
        List<Comment> replies = new ArrayList<>();
        long currentTime = System.currentTimeMillis();

        for (int i = 0; i < 2; i++) {
            String replyId = "reply_" + System.currentTimeMillis() + "_" + i;
            User user = generateMockUser(i + 10);
            String text = COMMENT_TEXTS[(i + 5) % COMMENT_TEXTS.length];
            long timestamp = currentTime - (i * Constants.HOUR_IN_MILLIS);
            int likeCount = (i + 1) * 2;

            Comment reply = new Comment(
                    replyId,
                    user,
                    text,
                    timestamp,
                    likeCount,
                    null,
                    false
            );

            replies.add(reply);
        }

        return replies;
    }

    /**
     * Gets the time difference string for display.
     *
     * @param timestamp Post creation timestamp
     * @return Human-readable time difference string
     */
    public static String getTimeDifferenceString(long timestamp) {
        long currentTime = System.currentTimeMillis();
        long difference = currentTime - timestamp;

        if (difference < Constants.HOUR_IN_MILLIS) {
            long minutes = difference / 60000;
            return minutes + " MIN" + (minutes > 1 ? "S" : "") + " AGO";
        } else if (difference < Constants.DAY_IN_MILLIS) {
            long hours = difference / Constants.HOUR_IN_MILLIS;
            return hours + " HOUR" + (hours > 1 ? "S" : "") + " AGO";
        } else if (difference < Constants.WEEK_IN_MILLIS) {
            long days = difference / Constants.DAY_IN_MILLIS;
            return days + " DAY" + (days > 1 ? "S" : "") + " AGO";
        } else {
            long weeks = difference / Constants.WEEK_IN_MILLIS;
            return weeks + " WEEK" + (weeks > 1 ? "S" : "") + " AGO";
        }
    }

    private MockDataGenerator() {
        // Prevent instantiation
    }
}

