# Smart Home State

Application to manage the state of an OpenHAB smart home system as a single state.

## Why?

I have been developing my home automation system for a while now and I have a lot of different components that I want to manage in a single state.
This application is designed to do just that.

Having multiple Items in an OpenHab system is error-prone and challenging to manage. As the number of items grows, and you have to have many
of them to manage single components, the odds of having inconsistencies or making mistakes increases.

This application has multiple inputs as events and as an output updates the items using the OpenHab REST API.