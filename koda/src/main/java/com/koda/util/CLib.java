/*******************************************************************************
* Copyright (c) 2013 Vladimir Rodionov. All Rights Reserved
*
* This code is released under the GNU Affero General Public License.
*
* See: http://www.fsf.org/licensing/licenses/agpl-3.0.html
*
* VLADIMIR RODIONOV MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY
* OF THE SOFTWARE, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
* IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, OR
* NON-INFRINGEMENT. Vladimir Rodionov SHALL NOT BE LIABLE FOR ANY DAMAGES SUFFERED
* BY LICENSEE AS A RESULT OF USING, MODIFYING OR DISTRIBUTING THIS SOFTWARE OR
* ITS DERIVATIVES.
*
* Author: Vladimir Rodionov
*
*******************************************************************************/
package com.koda.util;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.lang.reflect.Field;

import org.apache.log4j.Logger;

import com.sun.jna.LastErrorException;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;

// TODO: Auto-generated Javadoc
/**
 * The Class CLibrary.
 */
public final class CLib
{
    
    /** The logger. */
    private static Logger logger = Logger.getLogger(CLib.class);

    /** The Constant MCL_CURRENT. */
    public static final int MCL_CURRENT = 1;
    
    /** The Constant MCL_FUTURE. */
    public static final int MCL_FUTURE = 2;
    
    /** The Constant ENOMEM. */
    public static final int ENOMEM = 12;

    /** The Constant F_GETFL. */
    public static final int F_GETFL   = 3;  /* get file status flags */
    
    /** The Constant F_SETFL. */
    public static final int F_SETFL   = 4;  /* set file status flags */
    
    /** The Constant F_NOCACHE. */
    public static final int F_NOCACHE = 48; /* Mac OS X specific flag, turns cache on/off */
    
    public static final int F_RDAHEAD = 45; /* Mac OS X specific flag, turns read ahead on/off */
    
    /** The Constant O_DIRECT. */
    public static final int O_DIRECT  = 040000; /* fcntl.h */

    /** The Constant POSIX_FADV_NORMAL. */
    public static final int POSIX_FADV_NORMAL     = 0; /* fadvise.h */
    
    /** The Constant POSIX_FADV_RANDOM. */
    public static final int POSIX_FADV_RANDOM     = 1; /* fadvise.h */
    
    /** The Constant POSIX_FADV_SEQUENTIAL. */
    public static final int POSIX_FADV_SEQUENTIAL = 2; /* fadvise.h */
    
    /** The Constant POSIX_FADV_WILLNEED. */
    public static final int POSIX_FADV_WILLNEED   = 3; /* fadvise.h */
    
    /** The Constant POSIX_FADV_DONTNEED. */
    public static final int POSIX_FADV_DONTNEED   = 4; /* fadvise.h */
    
    /** The Constant POSIX_FADV_NOREUSE. */
    public static final int POSIX_FADV_NOREUSE    = 5; /* fadvise.h */
    
    public static final LibC LIBC = (LibC)Native.loadLibrary("c", LibC.class);
    
    private static boolean isLinux = System.getProperty("os.name").toLowerCase().contains("linux");

    private static boolean isMac = System.getProperty("os.name").toLowerCase().contains("mac");    
 
    /**
     * Errno.
     *
     * @param e the e
     * @return the int
     */
    private static int errno(RuntimeException e)
    {
        assert e instanceof LastErrorException;
        try
        {
            return ((LastErrorException) e).getErrorCode();
        }
        catch (NoSuchMethodError x)
        {
            logger.warn("Obsolete version of JNA present; unable to read errno. Upgrade to JNA 3.2.7 or later");
            return 0;
        }
    }

    /**
     * Instantiates a new c library.
     */
    private CLib() {}

    /**
     * Try mlockall.
     */
    public static void tryMlockall()
    {
        try
        {
            int result = LIBC.mlockall(MCL_CURRENT);
            if(result == 0){ // mlockall should always be zero on success
              logger.info("JNA mlockall successful");
            } else{
              logger.info("JNA mlockall failed.");
            }
        }
        catch (UnsatisfiedLinkError e)
        {
            // this will have already been logged by CLibrary, no need to repeat it
          e.printStackTrace();
        }
        catch (RuntimeException e)
        {
            if (!(e instanceof LastErrorException))
                throw e;
            if (errno(e) == ENOMEM && System.getProperty("os.name").toLowerCase().contains("linux"))
            {
                logger.warn("Unable to lock JVM memory (ENOMEM)."
                             + " This can result in part of the JVM being swapped out, especially with mmapped I/O enabled."
                             + " Increase RLIMIT_MEMLOCK or run application as root.");
            }
            else if (!System.getProperty("os.name").toLowerCase().contains("mac"))
            {
                // OS X allows mlockall to be called, but always returns an error
                logger.warn("Unknown mlockall error " + errno(e));
            }
        }
    }

    /**
     * Create a hard link for a given file.
     *
     * @param sourceFile      The name of the source file.
     * @param destinationFile The name of the destination file.
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public static void createHardLink(File sourceFile, File destinationFile) throws IOException
    {
        try
        {
            int result = LIBC.link(sourceFile.getAbsolutePath(), destinationFile.getAbsolutePath());
            assert result == 0; // success is always zero
        }
        catch (UnsatisfiedLinkError e)
        {
            createHardLinkWithExec(sourceFile, destinationFile);
        }
        catch (RuntimeException e)
        {
            if (!(e instanceof LastErrorException))
                throw e;
            // there are 17 different error codes listed on the man page.  punt until/unless we find which
            // ones actually turn up in practice.
            throw new IOException(String.format("Unable to create hard link from %s to %s (errno %d)",
                                                sourceFile, destinationFile, errno(e)));
        }
    }

    /**
     * Creates the hard link with exec.
     *
     * @param sourceFile the source file
     * @param destinationFile the destination file
     * @throws IOException Signals that an I/O exception has occurred.
     */
    public static void createHardLinkWithExec(File sourceFile, File destinationFile) throws IOException
    {
        String osname = System.getProperty("os.name");
        ProcessBuilder pb;
        if (osname.startsWith("Windows"))
        {
            float osversion = Float.parseFloat(System.getProperty("os.version"));
            if (osversion >= 6.0f)
            {
                pb = new ProcessBuilder("cmd", "/c", "mklink", "/H", destinationFile.getAbsolutePath(), sourceFile.getAbsolutePath());
            }
            else
            {
                pb = new ProcessBuilder("fsutil", "hardlink", "create", destinationFile.getAbsolutePath(), sourceFile.getAbsolutePath());
            }
        }
        else
        {
            pb = new ProcessBuilder("ln", sourceFile.getAbsolutePath(), destinationFile.getAbsolutePath());
            pb.redirectErrorStream(true);
        }
        Process p = pb.start();
        try
        {
            p.waitFor();
        }
        catch (InterruptedException e)
        {
            throw new RuntimeException(e);
        }
    }

    /**
     * Try skip cache.
     *
     * @param fd the fd
     * @param offset the offset
     * @param len the len
     */
    public static void trySkipCache(int fd, int offset, int len)
    {
        if (fd < 0)
            return;

        try
        {
            if (System.getProperty("os.name").toLowerCase().contains("linux"))
            {
              // flush data before - we need this to guarantee that
              // the skip cache takes effect.
              // In Linux we do this on file write only.
              LIBC.fdatasync(fd);              
              LIBC.posix_fadvise(fd, offset, len, POSIX_FADV_DONTNEED);
            }
            else if (System.getProperty("os.name").toLowerCase().contains("mac"))
            {
                tryFcntl(fd, F_NOCACHE, 1);
            }
        }
        catch (UnsatisfiedLinkError e)
        {
            // if JNA is unavailable just skipping Direct I/O
            // instance of this class will act like normal RandomAccessFile
          logger.warn(e.getMessage(), e);
        }
    }

    /**
     * Disable read ahead (for random file access )
     * @param fd - file descriptor
     * @param off - file offset
     * @param len - block len
     */
    public static void disableReadAhead(int fd, int off, int len){
      // posix_fadvise is not supported on some platforms (Mac OS X)
      // Make sure that 'off' and 'len' are integers (< Integer.MAX_VALUE)
      int result = -1;      
      if(isLinux){
        result = LIBC.posix_fadvise(fd, off, len, POSIX_FADV_RANDOM);
      } else if(isMac){
          result = tryFcntl(fd, F_RDAHEAD, 0);          
      }
      
      if(result < 0){
        logger.warn("Disable read ahead failed for fd:"+fd);
      }
    }
    
    /**
     * Set direct (raw) IO
     * @param fd - file descriptor
     * @return true if successful, false otherwise
     */
    public static boolean setRawIO(int fd)
    {
      if(isLinux){
        int flags = LIBC.fcntl(fd, F_GETFL, 0);
        if( flags == -1){
          logger.warn("fcntl F_GETFL failed");
          return false;
        }
        int result = LIBC.fcntl(fd, F_SETFL, flags | O_DIRECT);
        if(result == -1) {
          logger.warn("fcntl F_SETFL O_DIRECT failed"); 
          return false;
        }
        return true;
      }
      return false;
    }
    
    
    /**
     * Try fcntl.
     *
     * @param fd the fd
     * @param command the command
     * @param flags the flags
     * @return the int
     */
    public static int tryFcntl(int fd, int command, int flags)
    {
        int result = -1;

        try
        {
            result = LIBC.fcntl(fd, command, flags);
            if(result < 0) logger.error("fcntl failed");
            //assert result >= 0; // on error a value of -1 is returned and errno is set to indicate the error.
        }
        catch (RuntimeException e)
        {
            if (!(e instanceof LastErrorException))
                throw e;

            logger.warn(String.format("fcntl(%d, %d, %d) failed, errno (%d).",
                                      fd, command, flags, CLib.errno(e)));
        }

        return result;
    }

    /**
     * Get system file descriptor from FileDescriptor object.
     * @param descriptor - FileDescriptor objec to get fd from
     * @return file descriptor, -1 or error
     */
    public static int getfd(FileDescriptor descriptor)
    {
        Field field = getProtectedField(descriptor.getClass(), "fd");

        if (field == null)
            return -1;

        try
        {
            return field.getInt(descriptor);
        }
        catch (Exception e)
        {
            logger.warn("unable to read fd field from FileDescriptor");
        }

        return -1;
    }
    
    /**
     * Used to get access to protected/private field of the specified class
     * @param klass - name of the class
     * @param fieldName - name of the field
     * @return Field or null on error
     */
    @SuppressWarnings("unchecked")
    private static Field getProtectedField(Class klass, String fieldName)
    {
        Field field;

        try
        {
            field = klass.getDeclaredField(fieldName);
            field.setAccessible(true);
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }

        return field;
    }
    
    public static void main(String[] args){
      logger.info("Test CLibrary. Platform C name="+Platform.C_LIBRARY_NAME+" platform="+Platform.getOSType());
      CLibrary.tryMlockall();
    }
    
    public interface LibC extends Library{
      int mlockall(int flags);
      int munlockall();
      int link(String from, String to);
      // fcntl - manipulate file descriptor, `man 2 fcntl`
      /**
       * Fcntl.
       *
       * @param fd the fd
       * @param command the command
       * @param flags the flags
       * @return the int
       * @throws LastErrorException the last error exception
       */
      int fcntl(int fd, int command, long flags);

      // fadvice
      /**
       * Posix_fadvise.
       *
       * @param fd the fd
       * @param offset the offset
       * @param len the len
       * @param flag the flag
       * @return the int
       * @throws LastErrorException the last error exception
       */
      int posix_fadvise(int fd, int offset, int len, int flag);
      
      /**
       * File/ kernel sync operations
       * @param fd - the file descriptor
       * @return 0 - success, -1 otherwise  
       *   
       */
      
      void sync();
      
      int fsync(int fd);
      
      int fdatasync(int fd);
      
      /**
       * Seek in file
       * 
       * @param fd
       * @param newOffset
       * @return new offset or -1
       */
      
      int lseek(int fd, int newOffset, int whence);
      
      /**
       * Read from file
       * @param fd - file descriptor
       * @param size - total to read
       * @return 0 - EOF, -1 - error, number of read bytes.
       */
      int read(int fd, Pointer p, int size);
      
      void err(int err, String fmt);
      
    }
}
