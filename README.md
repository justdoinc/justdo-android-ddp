# JustDo Android DDP Sample App

This is an extremely simplified App, connecting to `https://alpha.justdo.com/` using hard-coded credentials, and automatically load the JustDo named `~23.5k Tasks / ~1.2k Users`. 

The original Android-DDP library is used, but the DDP Callback (`DDPCallback.java`) has been customised to store the received documents in a SQLite database, and to broadcast the DDP events that triggers various functions defined in `MainActivity.java`.  When DDP event occurs, the RecyclerView has set to refresh automatically at a fixed interval (250ms), to list out the items in the database.

This App is designed to demonstrate two issues:
1. When the network speed is fast (e.g. WIFI), the RecyclerView is unresponsive when the App is downloading the documents from the backend. This is due to too much work is being done in the DDP and Database operations on the UI thread. You may also observe the clock at the bottom right corner paused when the UI thread is overloaded.

2. When the network speed is slow (e.g. slow data network), the UI might still work smoothly, but the long data download time will trigger a DDP Disconnect event after the subscription is done.


## Requirements

 * Android Studio 4.0.1
 * Android device or simulator running Android 5.0+
 
 
