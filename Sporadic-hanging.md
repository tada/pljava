##Linux threads cause sporadic hanging##
It seems Java doesn't play nice with LinuxThreads. I rebuilt glibc to use the Native POSIX Thread Library (NPTL) and restarted PostgreSQL. Everything seems to be working so far. Here's how you can check what you have:

$ getconf GNU_LIBPTHREAD_VERSION  
linuxthreads-0.10

If you see linuxthreads, you need to upgrade. This is what you want to see:

$ getconf GNU_LIBPTHREAD_VERSION  
NPTL 2.3.6

(version might be higher) You can also get this information (and more) by running /libc.so.6:

$ /lib/libc.so.6  
...  
linuxthreads-0.10 by Xavier Leroy  
...  

or:

$ /lib/libc.so.6  
...  
Native POSIX Threads Library by Ulrich Drepper et al  
...

If you can't switch to NPTL for some reason, it might be possible to use LD_ASSUME_KERNEL to get things working on LinuxThreads.

###References###
http://docs.oracle.com/cd/E13924_01/coh.340/cohfaq/faq16702.htm<br/>
http://en.wikipedia.org/wiki/NPTL<br/>
http://gentoo-wiki.com/NPTL#Switching_to_NPTL<br/>
http://people.redhat.com/drepper/assumekernel.html<br/>
http://developer.novell.com/wiki/index.php/LD_ASSUME_KERNEL
