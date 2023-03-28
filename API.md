# API

## Notes on Downloading Files (Write up)
This uses curse forge to download the needed mods.
This was done through web-scraping instead of API calls. There are a
few things I would like to keep note about this facet. Really, I just
feel like writing them down, so I know what I did. Also, maybe someone
is, by chance, curious of my method.

The first being That I would like to use the direct download links,
however cloudflare have defenses against bots. It would be fun to
masquerade as a valid client, but I am not talented enough, right now,
also I doubt the people I made this for would even notice.
This is why I took a different approach, as well as attempting other
avenues.
### What It Does and How I Discovered It
What the program currently does is uses a map of known minecraft
versions and their id, I assume this is the pk of the db or something,
and stores this as a dictionary. I found these values by going an
arbitrary mod's _files_ page and changing the version. I would then
check the call it made to a _public_ API for the version ID of that
version.

### Prelude
This was found using the developer tools on Chrome, and I used it
for just about everything in this process. I knew the links had to
go somewhere, and I happened to stumble across a _public_ API
that is not defended by cloudflare, which makes scraping even basic
pages impossible.

### Details
Next was getting The mod's details. This is done from an API call
on the _files_ page that lists all the mods. The problem with this,
and scraping, is that I would either have to check every file until
I get the correct one. This is the same for scraping, although for
scraping, I would have to go to the next page. I learned that setting
the minecraft version decreases the size of the list, and is done
as a parameter to the API call. This means I can set the version id
to one in the list. This is why I created the list in the first place.
I set the version, and get a list of only what I need.

### API's Better, Than Scraping !?
This seems to be better than scraping. I get a list that is easy
to read and unlikely to change. A website is more apt to change, than
an API, even if it's only _public_. The API might secretly change,
but I can manage. Also the API can retrieve the whole list.

### The Final Download
With the _mod details_ as I call it, I can now retrieve the mod file
itself. You see, I kinda started here. This is the link **after** the
cloudflare, that is right, I essentially bypassed cloudflare, due to
_public_ information. But my problem at the start was I needed the
mod file name, and could not use the website to get it. Thankfully,
the _mod details_ gives the filename, as well as other nice
information I may use. As for this link, what does it need? I
discovered that it is the mod `fileId` (the id of that specific file,
no idea if they are globally unique), but split in _half_. It then
has the filename, which I could not get, and did not feel like
guessing. I want this _**fully automated**_. So now I can download
the file. And it works fine.

### But...
But, you know... there is something cool sounding about masquerading
as a valid client, only needing **one** API call. If I could
improve this later here is what I would do, in order of wanton.
1. Use the legit link (no duh)
2. Automate the map (I have to type it in by hand, which I hate)
   That's really it I think.

### Final Thoughts
That's all I have to say, I think. I may add more to this, or make
a whole new document for completeness. To be honest, writing this
only took a bit of time, and was actually fun. I may bury this in
some folder or something. And to a possible boss, future employer,
family member, friend, or _**Whoever**_ finds this.

"Hello, I hope you enjoyed this quick _little_ read on my antics.
I hope you enjoyed it in the morning, with a coffee/tea, or maybe
at night after a hard/long day, and wanted something to wind down
too. Maybe you even got a kick out of it. This is all fine and
good."

However. If you are the future me. I hope this instilled some
drive in you to fix/update this. I hope by now you/me have learned
the skills required to complete this task.

Until next time.
