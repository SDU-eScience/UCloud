# News Service

​

## Motivation

A support message from user "toasm15@student.sdu.dk"
​
> From: toasm15@student.sdu.dk
> SUGGESTION: In case of news "Like for example that some Nodes dropped out last week or so because of power shortage"
> you could have some kind of news board on the dashboard
​
This would allow us to broadcast messages to users on the platform, where the information will be relevant, instead of
sending an e-mail, that can be lost among other unread e-mails.
​
The messages could include problems the platform has suffered, as well as posts about new features for the platform.
​

## Design

The idea is to create a UI available for admins to create posts.
​
The posts will consist of three things: title, subtitle and content.
​
The subtitle is a short text that will be shown on the dashboard, and all three things will be shown when clicking on
a post to see more.
​
On the UCloud dashboard, an additional box will show the title, subtitle for the most recent post, and links to around 5
older posts.
​
Posts have a timestamp to be shown when passed but don't necessarily have an expiration date.
​
All posts are viewable for all users.

​

## Problems with described solution

- Dashboard is the landing page when logging in, but users can go directly to another page, never seeing a message that might be relevant for them.
- User A starts job with 200 hour runtime, power outage occurs, we post an update, user returns after the 200 hours and sees that the job was terminated early, and now has to be restarted, effectively being 200 hours behind.

​

### Possible solutions

​
Both points above would be solved by sending out an e-mail, hoping the users see them there, improving visibility of an issue.
The first point could be solved by also showing notifications for unread news.

​

## Possible further developments

With the current implementation, it is essentially a CMS for ADMINS. With a few changes, a project manager could provide
project-wide announcements.
