Sample Queries
1. Add subscriptions
  Send a POST query to the server's `/addSubscription` with the body
  ```
  [
	"https://xkcd.com/rss.xml",
	"https://www.wired.com/category/gear/feed/",
	"https://www.wired.com",
	"http://feeds.feedburner.com/Pidjin",
	"http://feeds.feedburner.com/zenpencils"
]
  ```
  
  Response is of the form
  ```
  {
    "success": [
        "https://xkcd.com/rss.xml",
        "https://www.wired.com/category/gear/feed/",
        "http://feeds.feedburner.com/Pidjin",
        "http://feeds.feedburner.com/zenpencils"
    ],
    "failure": [
        "https://www.wired.com"
    ]
}
  ```
  
2. Query for updates
  Send a POST query to the server's `/querySubscriptions` endpoint with the body
  
   i) when last-updated is not known
    ```
    {
	    "feeds": [
		    "http://feeds.feedburner.com/Pidjin",
		    "http://feeds.feedburner.com/zenpencils"
		  ]
    }
    ```
    ii) when last-updated is known
    ```
    {
	    "feeds": [
		    "http://feeds.feedburner.com/Pidjin",
		    "http://feeds.feedburner.com/zenpencils"
		  ],
	    "lastUpdated": 1526386000723
    }
    ```
    
    Response should be of the form
    ```
    {
      "lastUpdated": 1526388875690,
      "items": [
        {
          "author": "PIDJIN.NET",
          "source": "http://feeds.feedburner.com/Pidjin",
          "description": "<p>Fredo loves buying food.</p>\n<p>The post <a rel=\"nofollow\" href=\"http://www.pidjin.net/2017/08/30/dont-buy-food-hungry/\">Don&#8217;t buy food hungry</a> appeared first on <a rel=\"nofollow\" href=\"http://www.pidjin.net\">Fredo and Pidjin. The Webcomic.</a>.</p>\n",
          "link": "http://feedproxy.google.com/~r/Pidjin/~3/Q1hiS7UTsF4/",
          "pubDate": 1504096552000,
          "category": "Fredo & Pid'Jin",
          "title": "Don’t buy food hungry"
        },
        {
          "author": "Gav",
          "source": "http://feeds.feedburner.com/zenpencils",
          "description": "<p><a href=\"https://zenpencils.com/comic/fear/\" rel=\"bookmark\" title=\"217. The Monster Named Fear\"><img width=\"12\" height=\"96\" src=\"https://cdn-zenpencils.netdna-ssl.com/wp-content/uploads/217_fear1a.jpg\" class=\"attachment-thumbnail size-thumbnail wp-post-image\" alt=\"\" /></a>\n</p>This comic originally appeared exclusively in my second Zen Pencils book collection (available from all good retailers!) in 2015. I really enjoy writing these fun poems and wanted to do something special for the book. It features characters long-time readers [&#8230;] <a class=\"more-link\" href=\"https://zenpencils.com/comic/fear/\">&#8595; Read the rest of this entry...</a>",
          "link": "http://feedproxy.google.com/~r/zenpencils/~3/Hm7qdNTRYmY/",
          "pubDate": 1503361391000,
          "title": "217. The Monster Named Fear"
        }
      ]
    }
    ```
