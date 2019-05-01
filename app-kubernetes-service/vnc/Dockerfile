FROM ubuntu:18.10
RUN apt-get update
RUN apt-get install -y x11vnc xvfb firefox
ENV HOME=/
CMD x11vnc -forever -nopw -create -xkb
