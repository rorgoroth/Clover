## On the Vichan Posting WAF (Web Application Firewall)
Vichan docs are severely lacking and this [repo](https://github.com/vichan-devel/vichan-API/) is the only thing mentioning an API.
After adding a lot of debugging to `VichanAntispam.java`, `NetModule.java` and `MultipartHttpCall.java` we have a clearer picture of how Vichan works:

Vichan's bot protection relies on filtering out clients that submit programmatic "perfect" requests. In particular, automated tools typically send rigid, hardcoded lists of keys without regard for session state or acknowledging empty HTML forms.

To complete a valid post without getting the *"You look like a bot."* or *"Your request looks automated; Post discarded"* error (this was tested on Sushichan, so other sites might differ in their posting rules/spam criteria), several criteria must be met in unison across two phases:

### Phase 1: The Preparatory Stage (VichanAntispam.java)

**The Cookie Generation:**\
First we deliberately do an empty `GET /post.php` request.\
Vichan requires the client to establish a live *$PHPSESSID* session cookie to prove they've loaded the posting page. We fetch the target thread's HTML, parse the DOM with Jsoup, and locate the actual HTML `<form>`. We extract all hidden form tokens, which have randomized garbage names (such as yk1qhj...). A bot would normally miss these because they only exist in that dynamic page render.

To trick bots and scrapers, Vichan renames the "Comment" box inside the text area dynamically to something else (this changes per thread). We detect the renamed `<textarea>` and explicitly dump our native `reply.comment` into it.

### Phase 2: The API Delivery (VichanActions.java)

We construct the MultipartBody payload exactly as a real browsing engine would.
We append the hardcoded basic fields board, thread, and importantly we force it into returning pure JSON using `json_response=1`, bypassing the requirement of manually parsing redirect URLs.

Previously, if a user did not put a Title on their post, Clover logically dropped the subject from the MultipartHttpCall entirely (as in `if (!isEmpty(reply.subject))`).\
However, a real web browser form-data naturally submits empty fields. The `subject=""` parameter was missing from our initial payload footprint, making Clover immediately be indentified as a script or bot.

Clover was also missing the hidden form keys, randomly generated body `<textarea>` IDs, and the proper *$PHPSESSID* tracking.

## TL;DR: 

By enforcing empty fields in Java and mapping out the target thread's dynamically generated garbage keys, Clover looks entirely like a standard desktop web client-side browser behavior to Vichan's WAF and can successfully post.

- otacoo
