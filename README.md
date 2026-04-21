# SocialNine Android App - Home Screen Frontend Implementation

## Overview

This is a complete Android frontend implementation of the **SocialNine** social media app's home screen. The application is built using **Java** and **Android Studio**, following Material Design 3 guidelines and Android best practices.

## Project Structure

The project is organized into the following package structure for maintainability and scalability:

```
com.example.social_app/
├── activities/
│   └── MainActivity.java          # Main entry point, handles navigation
├── fragments/
│   └── HomeFragment.java          # Home feed fragment with RecyclerView
├── adapters/
│   └── PostAdapter.java           # RecyclerView adapter for posts and composer
├── models/
│   ├── Post.java                  # Post data model
│   └── User.java                  # User data model
└── utils/
    ├── Constants.java             # App-wide constants
    └── MockDataGenerator.java      # Mock data for demo purposes
```

### Resource Structure

```
res/
├── layout/
│   ├── activity_main.xml          # Main activity layout
│   ├── fragment_home.xml          # Home fragment layout with AppBar and RecyclerView
│   ├── item_post.xml              # Individual post card layout
│   └── item_post_composer.xml     # Post composer card layout
├── drawable/
│   ├── ic_bell.xml                # Notification bell icon
│   ├── ic_heart.xml               # Like icon (unfilled)
│   ├── ic_heart_filled.xml        # Like icon (filled/red)
│   ├── ic_comment.xml             # Comment icon
│   ├── ic_share.xml               # Share icon
│   ├── ic_bookmark.xml            # Bookmark/save icon
│   ├── ic_composer_image.xml      # Composer image action
│   ├── ic_composer_video.xml      # Composer video action
│   ├── ic_composer_location.xml   # Composer location action
│   ├── ic_composer_emoji.xml      # Composer emoji action
│   ├── avatar_placeholder.xml     # User avatar placeholder
│   ├── post_image_1.xml through post_image_4.xml  # Demo post images
│   └── bottom_nav_item_background.xml  # Bottom nav styling
├── menu/
│   └── bottom_nav_menu.xml        # Bottom navigation menu
├── values/
│   ├── colors.xml                 # Color palette (light mode)
│   ├── strings.xml                # String resources
│   └── themes.xml                 # Light mode theme
└── values-night/
    └── themes.xml                 # Dark mode theme
```

## Key Features

### 1. **AppBar/Header**
- Displays "SocialNine" in bold, purple color aligned to the left
- Notification bell icon aligned to the right
- Elevation shadow for visual separation

### 2. **Post Composer Card**
- Circular user avatar on the left
- EditText input with "What's new?" placeholder
- Media action buttons in a horizontal row:
  - 📷 Image
  - 🎬 Video
  - 📍 Location
  - 😊 Emoji
- Prominent circular "Post" button with purple background

### 3. **Social Feed**
Each post card includes:
- **User Header Section:**
  - Circular user avatar
  - Username (bold)
  - Timestamp (e.g., "2 HOURS AGO")
  - Optional location tag (e.g., "KYOTO, JAPAN")
  
- **Post Content:**
  - Main text with proper line spacing
  - Wide-format image with rounded corners and shadow
  
- **Action Bar:**
  - ❤️ Like button with count
  - 💬 Comment button with count
  - 🔗 Share button with count
  - 🔖 Bookmark icon (right-aligned)

### 4. **Bottom Navigation Bar**
Five navigation items with purple highlights:
- 🏠 Home (default/active)
- 🔍 Search
- ➕ Add (floating action button style)
- 💬 Message
- 👤 Profile

### 5. **Theme Support**
- **Light Mode:** Light backgrounds with dark text
- **Dark Mode:** Dark backgrounds with light text (default)
- **Color Palette:**
  - Primary: Purple (#7C3AED)
  - Accent: Red (#EF4444)
  - Surfaces: Adaptive colors based on theme

### 6. **Feed Features**
- **RecyclerView-based scrolling:** Smooth, performant vertical scrolling
- **Infinite Scroll:** Detects when reaching the bottom and loads more posts
- **State Management:**
  - Loading indicator (spinner)
  - Empty state message
  - Error state with retry button
- **Mock Data:** 10-15 sample posts with varied users, timestamps, and images

### 7. **Accessibility**
- All interactive elements have `contentDescription` attributes
- Touch targets meet Android's 48dp minimum guideline
- Semantic HTML structure with proper heading hierarchy
- High contrast colors for readability

## Technical Details

### Dependencies

The project uses the following key libraries:

```gradle
// Material Design 3
implementation("com.google.android.material:material:1.13.0")

// AndroidX Libraries
implementation("androidx.appcompat:appcompat:1.7.1")
implementation("androidx.activity:activity:1.12.2")
implementation("androidx.constraintlayout:constraintlayout:2.2.1")
implementation("androidx.fragment:fragment:1.6.2")

// Navigation
implementation("androidx.navigation:navigation-fragment:2.7.6")
implementation("androidx.navigation:navigation-ui:2.7.6")
```

### Architecture

- **MVVM-Ready:** Models (`Post`, `User`) are separate from UI logic
- **Adapter Pattern:** `PostAdapter` manages RecyclerView binding
- **Fragment-Based:** `HomeFragment` encapsulates home screen logic
- **Separation of Concerns:** Utilities, models, and UI are clearly separated

### Mock Data Generation

The `MockDataGenerator` class provides:
- Random user names and avatars
- Varied post content with realistic timestamps
- Optional location tags
- Engagement metrics (likes, comments, shares)
- Helper method `getTimeDifferenceString()` for human-readable time display

### Colors and Styling

**Light Mode (values/colors.xml):**
```xml
- primary_purple: #7C3AED
- light_bg_primary: #FFFFFF
- light_text_primary: #0F172A
```

**Dark Mode (values-night/colors.xml):**
```xml
- dark_bg_primary: #0F172A
- dark_bg_secondary: #1E293B
- dark_text_primary: #F1F5F9
```

## Building and Running

### Prerequisites
- Android Studio (latest version)
- Android SDK 24 (API level 24) or higher
- JDK 11 or higher

### Build Steps

1. **Open the project in Android Studio:**
   ```bash
   File → Open → Select the project folder
   ```

2. **Build the project:**
   ```bash
   Build → Make Project
   ```

3. **Run the app:**
   - Connect an Android device or launch an emulator
   - Click "Run" or press `Shift + F10`

### Testing on Emulator
- Recommended emulator: Android 13 (API 33) or higher
- Optimal screen size: 6.7" OLED for best visual testing

## Feature Demonstrations

### Like/Unlike Posts
- Click the heart icon to toggle like state
- Like count updates in real-time
- Heart icon changes from outline to filled (red)

### Infinite Scroll
- Scroll down to the bottom of the feed
- Loading indicator appears
- New posts are automatically loaded

### Navigation
- Click bottom navigation items to switch between sections
- Currently, only Home is fully implemented
- Other sections show toast messages (placeholders for future development)

### Dark Mode
- The app automatically respects system dark mode settings
- All colors adapt seamlessly between light and dark themes

## Code Comments and Documentation

The codebase is thoroughly documented with:
- **Class-level JavaDoc:** Describes the purpose of each class
- **Method-level JavaDoc:** Explains method functionality and parameters
- **Inline Comments:** Clarify complex logic and UI configurations

## Future Enhancements

To integrate this frontend with a backend API:

1. **API Integration:**
   - Replace `MockDataGenerator` with network calls using Retrofit or Volley
   - Implement `ViewModel` with `LiveData` for reactive UI updates
   - Add error handling and retry logic

2. **Image Loading:**
   - Integrate Glide or Coil for remote image loading
   - Add placeholder and error image handling

3. **Advanced Features:**
   - Implement real post composer with content submission
   - Add real-time notification system
   - Implement search and filtering
   - Add user profile screens
   - Implement messaging and comments

4. **Database:**
   - Add Room database for offline support
   - Implement local caching for posts

5. **Performance:**
   - Implement pagination with database queries
   - Add image caching and optimization
   - Use coroutines for background operations

## File Structure Summary

| File | Purpose |
|------|---------|
| `MainActivity.java` | Main activity, fragment management |
| `HomeFragment.java` | Home screen logic, RecyclerView setup |
| `PostAdapter.java` | RecyclerView adapter for posts |
| `Post.java` | Post data model |
| `User.java` | User data model |
| `MockDataGenerator.java` | Demo data generation |
| `Constants.java` | App-wide constants |
| `fragment_home.xml` | Home screen layout |
| `item_post.xml` | Post card layout |
| `item_post_composer.xml` | Composer card layout |
| `colors.xml` | Color definitions |
| `themes.xml` | Material 3 themes |
| Various `ic_*.xml` | Vector drawable icons |

## Design Principles Applied

✅ **Material Design 3 Compliance**
- Modern card-based UI
- Proper spacing and typography
- Adaptive color system

✅ **Android Best Practices**
- Fragment-based architecture
- Proper lifecycle management
- Resource organization
- Accessibility guidelines

✅ **Code Quality**
- Clean architecture
- Separation of concerns
- Reusable components
- Comprehensive documentation

✅ **User Experience**
- Smooth scrolling
- Responsive feedback
- Dark mode support
- Intuitive navigation

## Troubleshooting

**Issue: Build fails with missing dependencies**
- Solution: Sync Gradle with project files (File → Sync Now)

**Issue: Icons not displaying**
- Solution: Ensure drawable XML files are in the correct folder
- Check that all `android:src` references match file names

**Issue: Dark mode not applying**
- Solution: Check device is running Android 5.0+ with dark mode enabled
- Verify themes.xml files are in correct folders

**Issue: RecyclerView shows blank**
- Solution: Verify `fragment_home.xml` layout IDs match code references
- Check that `HomeFragment` is loaded in `MainActivity`

## Contributing

To extend this project:

1. Follow the existing package structure
2. Add new fragments to the `fragments/` package
3. Create new models in the `models/` package
4. Add layout files to `res/layout/`
5. Update strings in `res/values/strings.xml`
6. Document your changes with comments

## License

This project is created for educational purposes as part of a software engineering curriculum.

## Support

For issues or questions, refer to the inline code comments and JavaDoc documentation throughout the project.

---

**Project Version:** 1.0
**Last Updated:** 2025
**Target API Level:** 36
**Minimum API Level:** 24

